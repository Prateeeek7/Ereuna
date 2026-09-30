"""Stage 4: Fetch full text — download open-access PDFs and parse via GROBID.

For each ranked paper with an OA PDF URL, downloads the PDF, sends it to GROBID
for structured section extraction, and falls back to PyMuPDF if GROBID fails.
Papers without OA access are marked abstract-only with has_full_text=False.
"""

import asyncio
import logging
from typing import Callable, Optional

import httpx

from app.clients import europepmc
from app.clients.grobid import GrobidClient
from app.clients.semantic_scholar import SemanticScholarClient
from app.clients.unpaywall import UnpaywallClient
from app.models.paper import Paper, PaperSections, Section

logger = logging.getLogger(__name__)

# Concurrency limits
MAX_CONCURRENT_DOWNLOADS = 5
MAX_PDF_SIZE = 20 * 1024 * 1024  # 20 MB
DOWNLOAD_TIMEOUT = 40.0


async def _download_pdf(url: str) -> Optional[bytes]:
    """Download a PDF with size and time limits.

    Streams the response and gives up as soon as it is clearly not a PDF
    (an HTML landing or login page), instead of downloading the whole page.
    """
    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(DOWNLOAD_TIMEOUT, connect=10.0),
            follow_redirects=True,
            headers={"User-Agent": "Ereuna/0.1 (academic research tool)"},
        ) as client:
            async with client.stream("GET", url) as resp:
                if resp.status_code != 200:
                    logger.debug("PDF download HTTP %d: %s", resp.status_code, url)
                    return None
                content_type = resp.headers.get("content-type", "").lower()
                if "html" in content_type:
                    logger.debug("URL %s is an HTML page, not a PDF", url)
                    return None
                declared = int(resp.headers.get("content-length") or 0)
                if declared > MAX_PDF_SIZE:
                    logger.debug("PDF from %s exceeds size limit", url)
                    return None

                chunks: list[bytes] = []
                size = 0
                async for chunk in resp.aiter_bytes():
                    if not chunks and not chunk[:5].startswith(b"%PDF-") and len(chunk) >= 5:
                        logger.debug("URL %s did not return a PDF (bad magic bytes)", url)
                        return None
                    chunks.append(chunk)
                    size += len(chunk)
                    if size > MAX_PDF_SIZE:
                        logger.debug("PDF from %s exceeds size limit", url)
                        return None
                data = b"".join(chunks)
                return data if data[:5].startswith(b"%PDF-") else None

    except (httpx.TimeoutException, httpx.TransportError) as e:
        logger.debug("PDF download failed for %s: %s", url, e)
        return None
    except Exception as e:
        logger.debug("PDF download failed for %s: %s", url, e)
        return None


def _pymupdf_extract(pdf_bytes: bytes) -> Optional[PaperSections]:
    """Extract text from a PDF using PyMuPDF as a fallback.

    Args:
        pdf_bytes: Raw PDF bytes.

    Returns:
        PaperSections with page-level sections, or None on failure.
    """
    try:
        import fitz  # PyMuPDF

        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        sections: list[Section] = []

        for page_num in range(len(doc)):
            page = doc[page_num]
            text = page.get_text("text")
            if text and text.strip():
                sections.append(
                    Section(
                        heading=f"Page {page_num + 1}",
                        text=text.strip(),
                        page=page_num + 1,
                    )
                )

        doc.close()

        if not sections:
            return None

        result = PaperSections(sections=sections)
        result.build_full_text()
        return result

    except Exception as e:
        logger.debug("PyMuPDF extraction failed: %s", e)
        return None


def _abstract_only(paper: Paper) -> Paper:
    """Paper marked abstract-only, with the abstract as its single section."""
    if paper.abstract:
        sections = PaperSections(sections=[Section(heading="Abstract", text=paper.abstract)])
        sections.build_full_text()
        return paper.model_copy(update={"has_full_text": False, "sections": sections})
    return paper.model_copy(update={"has_full_text": False})


async def _parse_pdf(pdf_bytes: bytes, grobid: GrobidClient) -> Optional[PaperSections]:
    parsed = await grobid.process_pdf(pdf_bytes)
    if parsed is None:
        parsed = await asyncio.to_thread(_pymupdf_extract, pdf_bytes)
    if parsed and parsed.full_text and len(parsed.full_text) > 100:
        return parsed
    return None


async def _process_single_paper(
    paper: Paper,
    grobid: GrobidClient,
    unpaywall: UnpaywallClient,
    semaphore: asyncio.Semaphore,
) -> Paper:
    """Get full text for one paper from legal open-access sources, in order:

    1. the PDF link from the search source,
    2. every other PDF location Unpaywall knows for the DOI,
    3. Europe PMC's full text, for PubMed Central open-access articles.

    Falls back to abstract-only when none of them yields text.
    """
    async with semaphore:
        tried: list[str] = []

        async def try_pdf(url: str) -> Optional[PaperSections]:
            if not url or url in tried:
                return None
            tried.append(url)
            pdf_bytes = await _download_pdf(url)
            return await _parse_pdf(pdf_bytes, grobid) if pdf_bytes else None

        if paper.oa_pdf_url:
            parsed = await try_pdf(paper.oa_pdf_url)
            if parsed:
                return paper.model_copy(update={"has_full_text": True, "sections": parsed})

        oa_urls: list[str] = []
        if paper.doi:
            try:
                oa_urls = await unpaywall.get_oa_locations(paper.doi)
            except Exception as e:
                logger.debug("[%s] Unpaywall lookup failed: %s", paper.id, e)

        for url in oa_urls[:3]:
            parsed = await try_pdf(url)
            if parsed:
                return paper.model_copy(
                    update={"has_full_text": True, "sections": parsed, "oa_pdf_url": url}
                )

        pmcid = europepmc.find_pmcid(oa_urls + ([paper.oa_pdf_url] if paper.oa_pdf_url else []))
        if pmcid:
            parsed = await europepmc.fetch_fulltext(pmcid)
            if parsed and len(parsed.full_text) > 100:
                logger.debug("[%s] Full text from Europe PMC (%s)", paper.id, pmcid)
                return paper.model_copy(update={"has_full_text": True, "sections": parsed})

        return _abstract_only(paper)


async def _backfill_abstracts(papers: list[Paper]) -> list[Paper]:
    """Fill in missing abstracts / open-access PDF links from Semantic Scholar.

    OpenAlex omits abstracts for many publishers; S2 often has them. Only
    fields that are empty are filled, and only with S2's own record.
    """
    missing = [p for p in papers if (not p.abstract or not p.oa_pdf_url) and p.doi]
    if not missing:
        return papers
    s2 = SemanticScholarClient()
    try:
        records = await s2.batch_papers([f"DOI:{p.doi}" for p in missing], "abstract,openAccessPdf")
    except Exception as e:
        logger.debug("Abstract backfill failed: %s", e)
        return papers
    finally:
        await s2.close()

    updates: dict[str, dict] = {}
    for paper, rec in zip(missing, records):
        if not rec:
            continue
        upd: dict = {}
        if not paper.abstract and (rec.get("abstract") or "").strip():
            upd["abstract"] = " ".join(rec["abstract"].split())
        pdf = (rec.get("openAccessPdf") or {}).get("url")
        if not paper.oa_pdf_url and pdf:
            upd["oa_pdf_url"] = pdf
        if upd:
            updates[paper.id] = upd
    if updates:
        logger.info("Backfilled abstracts/PDF links for %d papers from Semantic Scholar", len(updates))
    return [p.model_copy(update=updates[p.id]) if p.id in updates else p for p in papers]


async def fetch_fulltext(
    papers: list[Paper],
    on_progress: Optional[Callable[[str], None]] = None,
) -> list[Paper]:
    """Download and parse open-access PDFs for a list of papers.

    For each paper with an OA PDF URL, downloads the PDF and sends it
    to GROBID for section extraction. Falls back to PyMuPDF if GROBID
    fails. Papers without OA access keep abstract-only and set
    has_full_text=false.

    Args:
        papers: Ranked papers from stage 3.
        on_progress: Optional callback for progress messages.

    Returns:
        Papers augmented with parsed sections or abstract-only flag.
    """
    if not papers:
        return papers

    papers = await _backfill_abstracts(papers)

    grobid = GrobidClient()
    unpaywall = UnpaywallClient()
    semaphore = asyncio.Semaphore(MAX_CONCURRENT_DOWNLOADS)

    papers_with_url = sum(1 for p in papers if p.oa_pdf_url)
    if on_progress:
        on_progress(
            f"Fetching full text for {papers_with_url} papers with OA links "
            f"(of {len(papers)} total)..."
        )

    try:
        # Process all papers concurrently with semaphore
        tasks = [
            _process_single_paper(paper, grobid, unpaywall, semaphore)
            for paper in papers
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        processed_papers: list[Paper] = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                logger.warning(
                    "[%s] Full-text fetch failed: %s",
                    papers[i].id,
                    result,
                )
                # Keep original paper with abstract-only
                p = papers[i]
                if p.abstract:
                    abstract_sections = PaperSections(
                        sections=[Section(heading="Abstract", text=p.abstract)],
                    )
                    abstract_sections.build_full_text()
                    processed_papers.append(
                        p.model_copy(
                            update={
                                "has_full_text": False,
                                "sections": abstract_sections,
                            }
                        )
                    )
                else:
                    processed_papers.append(p.model_copy(update={"has_full_text": False}))
            else:
                processed_papers.append(result)

        full_text_count = sum(1 for p in processed_papers if p.has_full_text)
        abstract_count = len(processed_papers) - full_text_count
        parser = "GROBID" if await grobid.is_alive() else "PyMuPDF"

        if on_progress:
            on_progress(
                f"Full text parsed ({parser}): {full_text_count} papers. "
                f"Abstract-only: {abstract_count} papers."
            )

        return processed_papers

    finally:
        await grobid.close()
        await unpaywall.close()
