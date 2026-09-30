"""GROBID client — sends PDFs and parses TEI XML into structured sections.

GROBID (GeneRation Of BIbliographic Data) is a machine learning library for
extracting structured information from scholarly documents. This client sends
PDF bytes to a running GROBID service and parses the returned TEI XML.
"""

import logging
import re
import xml.etree.ElementTree as ET
from typing import Optional

import httpx

from app.config import settings
from app.models.paper import PaperSections, Section

logger = logging.getLogger(__name__)

# TEI XML namespace
TEI_NS = "http://www.tei-c.org/ns/1.0"
NS = {"tei": TEI_NS}


class GrobidClient:
    """Client for GROBID PDF parsing service."""

    def __init__(self, grobid_url: Optional[str] = None) -> None:
        self._url = (grobid_url or settings.grobid_url).rstrip("/")
        self._client = httpx.AsyncClient(timeout=120.0)
        self._available: Optional[bool] = None

    async def is_alive(self) -> bool:
        """Check if GROBID service is running."""
        if self._available is not None:
            return self._available
        try:
            resp = await self._client.get(f"{self._url}/api/isalive", timeout=5.0)
            self._available = resp.status_code == 200
        except Exception as e:
            logger.debug("GROBID not available at %s: %s", self._url, e)
            self._available = False
        return self._available

    async def process_pdf(self, pdf_bytes: bytes) -> Optional[PaperSections]:
        """Send a PDF to GROBID and parse the TEI XML response into sections.

        Args:
            pdf_bytes: Raw bytes of the PDF file.

        Returns:
            PaperSections with parsed sections, or None if GROBID fails.
        """
        if not await self.is_alive():
            logger.debug("GROBID not available, skipping PDF processing")
            return None

        try:
            resp = await self._client.post(
                f"{self._url}/api/processFulltextDocument",
                files={"input": ("paper.pdf", pdf_bytes, "application/pdf")},
                data={
                    "consolidateHeader": "1",
                    "consolidateCitations": "0",
                    "includeRawAffiliations": "0",
                    "includeRawCitations": "0",
                    "segmentSentences": "0",
                },
                timeout=120.0,
            )
            resp.raise_for_status()
            tei_xml = resp.text
            return parse_tei_xml(tei_xml)

        except httpx.TimeoutException:
            logger.warning("GROBID request timed out for PDF (%d bytes)", len(pdf_bytes))
            return None
        except httpx.HTTPStatusError as e:
            logger.warning("GROBID returned error: %s", e.response.status_code)
            return None
        except Exception as e:
            logger.warning("GROBID processing failed: %s", e)
            return None

    async def close(self) -> None:
        """Close the HTTP client."""
        await self._client.aclose()


def _get_text(element: Optional[ET.Element]) -> str:
    """Extract all text content from an XML element and its children."""
    if element is None:
        return ""
    texts = []
    if element.text:
        texts.append(element.text)
    for child in element:
        texts.append(_get_text(child))
        if child.tail:
            texts.append(child.tail)
    return " ".join(texts).strip()


def _clean_text(text: str) -> str:
    """Clean extracted text: normalize whitespace, remove artifacts."""
    if not text:
        return ""
    # Collapse whitespace
    text = re.sub(r"\s+", " ", text).strip()
    return text


def parse_tei_xml(tei_xml: str) -> PaperSections:
    """Parse GROBID TEI XML into structured sections.

    Extracts the abstract from the header and body sections from the text element.

    Args:
        tei_xml: The TEI XML string from GROBID.

    Returns:
        PaperSections with parsed sections and concatenated full text.
    """
    sections: list[Section] = []

    try:
        root = ET.fromstring(tei_xml)
    except ET.ParseError as e:
        logger.warning("Failed to parse TEI XML: %s", e)
        return PaperSections(sections=[], full_text="")

    # Extract abstract from header
    abstract_el = root.find(f".//{{{TEI_NS}}}profileDesc/{{{TEI_NS}}}abstract")
    if abstract_el is not None:
        abstract_text = _clean_text(_get_text(abstract_el))
        if abstract_text:
            sections.append(Section(heading="Abstract", text=abstract_text))

    # Extract body sections
    body = root.find(f".//{{{TEI_NS}}}body")
    if body is not None:
        for div in body.findall(f"./{{{TEI_NS}}}div"):
            heading = ""
            head_el = div.find(f"{{{TEI_NS}}}head")
            if head_el is not None:
                heading = _clean_text(_get_text(head_el))
                # Extract section number if present
                n_attr = head_el.get("n", "")
                if n_attr and heading and not heading.startswith(n_attr):
                    heading = f"{n_attr} {heading}"

            # Collect paragraph text within this div
            paragraphs = []
            for p in div.findall(f"{{{TEI_NS}}}p"):
                p_text = _clean_text(_get_text(p))
                if p_text:
                    paragraphs.append(p_text)

            # Also get direct text from non-paragraph children
            if not paragraphs:
                direct_text = _clean_text(_get_text(div))
                # Remove heading text from direct text
                if heading and direct_text.startswith(heading):
                    direct_text = direct_text[len(heading):].strip()
                if direct_text:
                    paragraphs.append(direct_text)

            section_text = "\n".join(paragraphs)
            if section_text or heading:
                sections.append(Section(heading=heading, text=section_text))

    # Extract back matter (acknowledgments, appendices)
    back = root.find(f".//{{{TEI_NS}}}back")
    if back is not None:
        for div in back.findall(f".//{{{TEI_NS}}}div"):
            head_el = div.find(f"{{{TEI_NS}}}head")
            heading = _clean_text(_get_text(head_el)) if head_el is not None else ""

            paragraphs = []
            for p in div.findall(f"{{{TEI_NS}}}p"):
                p_text = _clean_text(_get_text(p))
                if p_text:
                    paragraphs.append(p_text)

            if paragraphs:
                sections.append(Section(heading=heading, text="\n".join(paragraphs)))

    result = PaperSections(sections=sections)
    result.build_full_text()
    return result
