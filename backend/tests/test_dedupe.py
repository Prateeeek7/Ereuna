"""Tests for paper deduplication logic."""

import pytest

from app.models.paper import Author, Paper
from app.pipeline.dedupe import (
    dedupe_papers,
    generate_short_label,
    merge_papers,
    normalize_doi,
    normalize_title,
    title_similarity,
)


def test_normalize_doi():
    assert normalize_doi("https://doi.org/10.1109/JSSC.2020.2987112") == "10.1109/jssc.2020.2987112"
    assert normalize_doi("http://dx.doi.org/10.1145/3386569.3392410") == "10.1145/3386569.3392410"
    assert normalize_doi("doi:10.1063/5.0089120") == "10.1063/5.0089120"
    assert normalize_doi("10.1109/TVLSI.2022.3164019") == "10.1109/tvlsi.2022.3164019"
    assert normalize_doi(None) is None
    assert normalize_doi("") is None


def test_normalize_title():
    raw = "A 0.4V, 7-nm Independent-Gate FinFET SRAM: With Dynamic Reverse Body-Biasing!"
    expected = "a 04v 7nm independentgate finfet sram with dynamic reverse bodybiasing"
    assert normalize_title(raw) == expected
    assert normalize_title("") == ""


def test_title_similarity():
    t1 = "A 0.4V 7nm Independent-Gate FinFET SRAM With Dynamic Reverse Body Biasing"
    t2 = "A 0.4V 7nm Independent Gate FinFET SRAM with Dynamic Reverse Body Biasing"
    t3 = "Cryogenic Characterization of FinFET SRAM Operation at 4.2 Kelvin"

    assert title_similarity(t1, t2) > 0.95
    assert title_similarity(t1, t3) < 0.50
    assert title_similarity(t1, t1) == 1.0


def test_generate_short_label():
    p1 = Paper(
        id="p1",
        title="Test Title",
        authors=[Author(name="Elena Rostova"), Author(name="Marcus Vance")],
        year=2020,
    )
    assert generate_short_label(p1) == "Rostova '20"

    p2 = Paper(id="p2", title="Test 2", year=2023)
    assert generate_short_label(p2) == "Unknown '23"


def test_merge_papers():
    p1 = Paper(
        id="p1",
        doi="10.1109/test.1",
        title="Short Title",
        authors=[Author(name="Author One")],
        year=2020,
        citation_count=10,
        abstract="Short abstract",
        oa_pdf_url=None,
        has_full_text=False,
    )
    p2 = Paper(
        id="p2",
        doi="10.1109/test.1",
        title="A Longer More Detailed Title",
        authors=[],
        year=2019,
        citation_count=25,
        abstract="A much longer and comprehensive abstract with details",
        oa_pdf_url="https://example.com/paper.pdf",
        has_full_text=True,
    )

    merged = merge_papers(p1, p2)
    assert merged.doi == "10.1109/test.1"
    assert merged.title == "A Longer More Detailed Title"
    assert len(merged.authors) == 1
    assert merged.year == 2019  # Earliest year
    assert merged.citation_count == 25  # Max citations
    assert merged.abstract == "A much longer and comprehensive abstract with details"
    assert merged.oa_pdf_url == "https://example.com/paper.pdf"
    assert merged.has_full_text is True


def test_dedupe_papers_by_doi():
    p1 = Paper(id="p1", doi="10.1109/jssc.2020.2987112", title="Title A", year=2020, citation_count=10)
    p2 = Paper(id="p2", doi="https://doi.org/10.1109/JSSC.2020.2987112", title="Title A Variant", year=2020, citation_count=20)
    p3 = Paper(id="p3", doi="10.1063/5.0089120", title="Distinct Paper", year=2023, citation_count=5)

    deduped = dedupe_papers([p1, p2, p3])
    assert len(deduped) == 2
    assert any(p.citation_count == 20 for p in deduped)


def test_dedupe_papers_by_title_levenshtein():
    # Same title without DOI
    p1 = Paper(
        id="p1",
        doi=None,
        title="Variability-Resilient 10T Subthreshold FinFET SRAM with Differential Read Buffer",
        year=2021,
    )
    p2 = Paper(
        id="p2",
        doi=None,
        title="Variability Resilient 10T Subthreshold FinFET SRAM With Differential Read Buffer",
        year=2021,
    )
    p3 = Paper(
        id="p3",
        doi=None,
        title="Something Entirely Different in Solid-State Electronics",
        year=2022,
    )

    deduped = dedupe_papers([p1, p2, p3], similarity_threshold=0.92)
    assert len(deduped) == 2
