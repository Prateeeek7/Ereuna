"""Stage 2: Retrieve and deduplicate candidate papers.

Queries OpenAlex, Semantic Scholar, and arXiv in parallel using the expanded queries,
transforms raw records into standardized Paper models, and deduplicates by DOI and title.
"""

import asyncio
import logging
from typing import Optional

from app.clients.arxiv_client import ArxivClient
from app.clients.openalex import OpenAlexClient, reconstruct_abstract
from app.clients.semantic_scholar import SemanticScholarClient
from app.models.paper import Author, Paper
from app.pipeline.dedupe import dedupe_papers, generate_short_label, normalize_doi

logger = logging.getLogger(__name__)

SOURCE_TIMEOUT_SECONDS = 45.0


def _clean(text: Optional[str]) -> str:
    # Some records carry a literal backslash-n from their source.
    return " ".join((text or "").replace("\\n", " ").split())


def openalex_to_paper(work: dict) -> Optional[Paper]:
    """Convert raw OpenAlex work dictionary to a Paper model."""
    title = _clean(work.get("title"))
    if not title:
        return None

    raw_id = work.get("id") or ""
    paper_id = raw_id.split("/")[-1] if "/" in raw_id else raw_id
    if not paper_id.startswith("openalex_"):
        paper_id = f"oa_{paper_id}"

    doi = normalize_doi(work.get("doi"))
    year = work.get("publication_year") or 0

    # Primary location / venue
    venue = ""
    prim_loc = work.get("primary_location") or {}
    source = prim_loc.get("source") or {}
    if source.get("display_name"):
        venue = source["display_name"]
    elif work.get("host_venue", {}).get("name"):
        venue = work["host_venue"]["name"]

    citations = work.get("cited_by_count") or 0
    abstract = _clean(reconstruct_abstract(work.get("abstract_inverted_index")))

    # Open Access PDF
    oa_pdf = None
    oa_info = work.get("open_access") or {}
    if oa_info.get("oa_url") and oa_info["oa_url"].endswith(".pdf"):
        oa_pdf = oa_info["oa_url"]
    elif prim_loc.get("pdf_url"):
        oa_pdf = prim_loc["pdf_url"]

    # Authors
    authors = []
    for authorship in work.get("authorships") or []:
        author_data = authorship.get("author") or {}
        name = author_data.get("display_name")
        if name:
            authors.append(Author(name=name))

    paper = Paper(
        id=paper_id,
        doi=doi,
        title=title,
        authors=authors,
        year=year,
        venue=venue,
        citation_count=citations,
        abstract=abstract,
        oa_pdf_url=oa_pdf,
        has_full_text=bool(oa_pdf),
    )
    paper.short_label = generate_short_label(paper)
    return paper


def semantic_scholar_to_paper(p: dict) -> Optional[Paper]:
    """Convert raw Semantic Scholar dictionary to a Paper model."""
    title = _clean(p.get("title"))
    if not title:
        return None

    s2_id = p.get("paperId") or ""
    if not s2_id:
        return None
    paper_id = f"s2_{s2_id}"

    ext_ids = p.get("externalIds") or {}
    doi = normalize_doi(ext_ids.get("DOI"))

    year = p.get("year") or 0
    venue = p.get("venue") or ""
    citations = p.get("citationCount") or 0
    abstract = p.get("abstract") or ""

    oa_pdf = (p.get("openAccessPdf") or {}).get("url")

    authors = [
        Author(name=a["name"].strip())
        for a in (p.get("authors") or [])
        if a.get("name") and a["name"].strip()
    ]

    paper = Paper(
        id=paper_id,
        doi=doi,
        title=title,
        authors=authors,
        year=year,
        venue=venue,
        citation_count=citations,
        abstract=abstract,
        oa_pdf_url=oa_pdf,
        has_full_text=bool(oa_pdf),
    )
    paper.short_label = generate_short_label(paper)
    return paper


def arxiv_to_paper(entry: dict) -> Optional[Paper]:
    """Convert parsed arXiv dictionary to a Paper model."""
    title = _clean(entry.get("title"))
    if not title:
        return None

    if not entry.get("id"):
        return None
    authors = [Author(name=a["name"]) for a in entry.get("authors") or []]

    paper = Paper(
        id=entry.get("id") or "",
        doi=normalize_doi(entry.get("doi")),
        title=title,
        authors=authors,
        year=entry.get("year") or 0,
        venue=entry.get("venue") or "arXiv",
        citation_count=entry.get("citation_count") or 0,
        abstract=entry.get("abstract") or "",
        oa_pdf_url=entry.get("oa_pdf_url"),
        has_full_text=bool(entry.get("oa_pdf_url")),
    )
    paper.short_label = generate_short_label(paper)
    return paper


async def retrieve_candidates(
    queries: list[str],
    openalex_client: Optional[OpenAlexClient] = None,
    s2_client: Optional[SemanticScholarClient] = None,
    arxiv_client: Optional[ArxivClient] = None,
    max_per_source: int = 50,
) -> list[Paper]:
    """Retrieve papers matching queries across 3 sources in parallel and deduplicate them.

    Args:
        queries: List of 4–6 expanded search queries.
        openalex_client: OpenAlex API client.
        s2_client: Semantic Scholar API client.
        arxiv_client: arXiv API client.
        max_per_source: Number of papers to query per search phrase.

    Returns:
        Deduplicated list of 100–300 candidate Paper objects.
    """
    oa = openalex_client or OpenAlexClient()
    s2 = s2_client or SemanticScholarClient()
    ar = arxiv_client or ArxivClient()

    # Query sources with the most relevant phrases (top 3 for OpenAlex, top 2 for S2 and arXiv)
    oa_queries = queries[:3]
    s2_queries = queries[:2]
    ar_queries = queries[:2]

    # Run source queries
    tasks = []
    for q in oa_queries:
        tasks.append(("openalex", oa.search_works(q, per_page=max_per_source)))
    for q in s2_queries:
        tasks.append(("s2", s2.search_papers(q, limit=max_per_source)))
    for q in ar_queries:
        tasks.append(("arxiv", ar.search(q, max_results=max_per_source)))

    # One slow or hanging source must not stall the whole map: cap each call.
    results = await asyncio.gather(
        *(asyncio.wait_for(t[1], timeout=SOURCE_TIMEOUT_SECONDS) for t in tasks),
        return_exceptions=True,
    )

    raw_candidates: list[Paper] = []

    for i, res in enumerate(results):
        source_name = tasks[i][0]
        if isinstance(res, asyncio.TimeoutError):
            logger.warning("%s query timed out after %.0fs; skipping it", source_name, SOURCE_TIMEOUT_SECONDS)
            continue
        if isinstance(res, Exception):
            logger.warning("%s query failed: %s", source_name, res)
            continue
        if not isinstance(res, list):
            continue

        for item in res:
            if not isinstance(item, dict):
                continue
            paper = None
            if source_name == "openalex":
                paper = openalex_to_paper(item)
            elif source_name == "s2":
                paper = semantic_scholar_to_paper(item)
            elif source_name == "arxiv":
                paper = arxiv_to_paper(item)

            if paper:
                raw_candidates.append(paper)


    logger.info(
        "Retrieved %d raw candidates across %d queries; starting deduplication...",
        len(raw_candidates),
        len(queries),
    )

    # Deduplicate candidates across all sources
    deduped = dedupe_papers(raw_candidates)
    logger.info("Deduplication complete: %d unique papers remaining.", len(deduped))
    return deduped
