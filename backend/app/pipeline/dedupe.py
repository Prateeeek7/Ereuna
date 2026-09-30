"""Paper deduplication engine.

Implements two-stage deduplication:
1. Exact match on normalized DOI.
2. Fuzzy match on normalized title (Levenshtein / SequenceMatcher ratio > 0.92).
Merges paper metadata across multiple sources (OpenAlex, Semantic Scholar, arXiv).
"""

import difflib
import re
from typing import Optional

from app.models.paper import Author, Paper


def normalize_doi(doi: Optional[str]) -> Optional[str]:
    """Normalize a DOI string into a canonical lowercase format."""
    if not doi:
        return None
    cleaned = doi.strip().lower()
    # Strip URL prefixes
    for prefix in [
        "https://doi.org/",
        "http://doi.org/",
        "https://dx.doi.org/",
        "http://dx.doi.org/",
        "doi:",
    ]:
        if cleaned.startswith(prefix):
            cleaned = cleaned[len(prefix) :].strip()
    return cleaned if cleaned else None


def normalize_title(title: str) -> str:
    """Normalize a title by lowercasing, removing punctuation, and collapsing whitespace."""
    if not title:
        return ""
    lowered = title.lower()
    # Strip punctuation, keeping alphanumeric characters and single whitespace
    stripped = re.sub(r"[^\w\s]", "", lowered)
    return " ".join(stripped.split())


def title_similarity(title1: str, title2: str) -> float:
    """Compute normalized title similarity using difflib ratio."""
    norm1 = normalize_title(title1)
    norm2 = normalize_title(title2)
    if not norm1 or not norm2:
        return 0.0
    if norm1 == norm2:
        return 1.0
    return difflib.SequenceMatcher(None, norm1, norm2).ratio()


def generate_short_label(paper: Paper) -> str:
    """Generate concise academic citation label like 'Rostova '20'."""
    if paper.short_label:
        return paper.short_label

    year_str = str(paper.year)[-2:] if paper.year > 0 else "n.d."

    author_name = "Unknown"
    if paper.authors:
        full_name = paper.authors[0].name.strip()
        # Last word is typically the surname
        author_name = full_name.split()[-1] if full_name else "Unknown"

    return f"{author_name} '{year_str}"


def merge_papers(primary: Paper, secondary: Paper) -> Paper:
    """Merge metadata from two duplicate paper records."""
    # Retain the most complete title (prefer longer title)
    best_title = primary.title if len(primary.title) >= len(secondary.title) else secondary.title

    # Retain canonical DOI
    best_doi = primary.doi or secondary.doi

    # Authors: take non-empty list
    best_authors = primary.authors if primary.authors else secondary.authors

    # Year: take earliest valid publication year
    valid_years = [y for y in [primary.year, secondary.year] if y > 1900]
    best_year = min(valid_years) if valid_years else (primary.year or secondary.year)

    # Venue: take longer / more specific venue string
    best_venue = primary.venue if len(primary.venue) >= len(secondary.venue) else secondary.venue

    # Citation count: take highest reported citation count
    best_citations = max(primary.citation_count, secondary.citation_count)

    # Abstract: retain longest text
    best_abstract = (
        primary.abstract if len(primary.abstract) >= len(secondary.abstract) else secondary.abstract
    )

    # Open Access PDF link
    best_oa_pdf = primary.oa_pdf_url or secondary.oa_pdf_url
    has_full_text = bool(best_oa_pdf or primary.has_full_text or secondary.has_full_text)

    # Short label
    merged_paper = Paper(
        id=primary.id,
        doi=best_doi,
        title=best_title,
        authors=best_authors,
        year=best_year,
        venue=best_venue,
        citation_count=best_citations,
        abstract=best_abstract,
        oa_pdf_url=best_oa_pdf,
        has_full_text=has_full_text,
        relevance_score=max(primary.relevance_score, secondary.relevance_score),
        relevance_reason=primary.relevance_reason or secondary.relevance_reason,
    )
    merged_paper.short_label = generate_short_label(merged_paper)
    return merged_paper


def dedupe_papers(papers: list[Paper], similarity_threshold: float = 0.92) -> list[Paper]:
    """Deduplicate a list of papers by DOI, then by normalized title similarity (>0.92).

    Args:
        papers: Raw list of papers collected from multiple sources.
        similarity_threshold: Levenshtein ratio threshold for matching titles (default 0.92).

    Returns:
        Deduplicated, merged list of unique papers.
    """
    if not papers:
        return []

    # Step 1: Deduplicate by DOI
    doi_map: dict[str, Paper] = {}
    no_doi_papers: list[Paper] = []

    for p in papers:
        norm_doi = normalize_doi(p.doi)
        if norm_doi:
            if norm_doi in doi_map:
                doi_map[norm_doi] = merge_papers(doi_map[norm_doi], p)
            else:
                p.doi = norm_doi
                doi_map[norm_doi] = p
        else:
            no_doi_papers.append(p)

    candidates = list(doi_map.values()) + no_doi_papers

    # Step 2: Deduplicate by normalized title similarity
    merged_unique: list[Paper] = []

    for candidate in candidates:
        match_idx = -1
        for i, existing in enumerate(merged_unique):
            sim = title_similarity(candidate.title, existing.title)
            if sim >= similarity_threshold:
                match_idx = i
                break

        if match_idx >= 0:
            merged_unique[match_idx] = merge_papers(merged_unique[match_idx], candidate)
        else:
            if not candidate.short_label:
                candidate.short_label = generate_short_label(candidate)
            merged_unique.append(candidate)

    return merged_unique
