"""Europe PMC client — open-access full text for PubMed Central articles.

Uses the official REST service (no key). Only articles in the Europe PMC
open-access subset have full text available; for others this returns None.
Docs: https://europepmc.org/RestfulWebService
"""

import logging
import re
import xml.etree.ElementTree as ET
from typing import Optional

import httpx

from app.config import settings
from app.models.paper import PaperSections, Section

logger = logging.getLogger(__name__)

BASE_URL = "https://www.ebi.ac.uk/europepmc/webservices/rest"

_PMCID_RE = re.compile(r"(?:pmc/articles/|PMCID:?\s*|europepmc\.org/articles?/)(?:PMC)?(\d{4,9})", re.IGNORECASE)


def find_pmcid(urls: list[str]) -> Optional[str]:
    """Extract a PMC id ("PMC1234567") from any of the given URLs."""
    for url in urls:
        m = _PMCID_RE.search(url or "")
        if m:
            return f"PMC{m.group(1)}"
    return None


def _text(el: Optional[ET.Element]) -> str:
    return " ".join("".join(el.itertext()).split()) if el is not None else ""


def parse_jats(xml_text: str) -> Optional[PaperSections]:
    """Parse JATS full-text XML into sections (abstract + body sections)."""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None

    sections: list[Section] = []
    abstract = root.find(".//front//abstract")
    if abstract is not None and _text(abstract):
        sections.append(Section(heading="Abstract", text=_text(abstract)))

    body = root.find(".//body")
    if body is not None:
        for sec in body.findall("./sec"):
            heading = _text(sec.find("title"))
            paragraphs = [_text(p) for p in sec.iter("p") if _text(p)]
            if paragraphs:
                sections.append(Section(heading=heading, text="\n".join(paragraphs)))
        if len(sections) <= 1:  # body without <sec> wrappers
            paragraphs = [_text(p) for p in body.findall("./p") if _text(p)]
            if paragraphs:
                sections.append(Section(heading="", text="\n".join(paragraphs)))

    if not sections:
        return None
    result = PaperSections(sections=sections)
    result.build_full_text()
    return result


async def fetch_fulltext(pmcid: str) -> Optional[PaperSections]:
    """Full text for an open-access PMC article, or None if not available."""
    user_agent = "Ereuna/0.1" + (f" (mailto:{settings.crossref_email})" if settings.crossref_email else "")
    try:
        async with httpx.AsyncClient(timeout=30.0, headers={"User-Agent": user_agent}) as client:
            resp = await client.get(f"{BASE_URL}/{pmcid}/fullTextXML")
            if resp.status_code != 200:
                return None
            return parse_jats(resp.text)
    except Exception as e:
        logger.debug("Europe PMC full text failed for %s: %s", pmcid, e)
        return None
