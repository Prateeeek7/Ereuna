"""Tests for pipeline stages 1–3 (expand_query, retrieve, rank)."""

import pytest

from app.models.map import MapFilters
from app.models.paper import Author, Paper
from app.pipeline.expand_query import expand_query, heuristic_expand_query
from app.pipeline.rank import rank_and_filter_papers, score_recency, score_venue


@pytest.mark.asyncio
async def test_expand_query():
    topic = "Low-leakage SRAM using FinFET"
    queries = await expand_query(topic)
    assert len(queries) >= 3
    assert len(queries) <= 6
    assert any("sram" in q.lower() for q in queries)
    assert any("finfet" in q.lower() for q in queries)


def test_heuristic_query_expansion():
    queries = heuristic_expand_query("Solid-state battery electrolyte degradation")
    assert queries[0] == "Solid-state battery electrolyte degradation"
    assert all("battery" in q.lower() for q in queries)


def test_heuristic_expansion_only_uses_topic_words():
    topic = "Mixture-of-experts routing for efficient large language model inference"
    queries = heuristic_expand_query(topic)
    assert queries[0] == topic
    topic_words = set(topic.lower().replace("-", " ").split())
    for q in queries:
        assert set(q.lower().replace("-", " ").split()) <= topic_words


def test_score_venue():
    assert score_venue("IEEE Journal of Solid-State Circuits") == 1.0
    assert score_venue("ACM/IEEE ISLPED") == 1.0
    assert score_venue("Nature Materials") == 1.0
    assert score_venue("arXiv preprint") == 0.5
    assert score_venue("Some Random Blog") == 0.4
    assert score_venue("") == 0.3


def test_score_recency():
    assert score_recency(2024, current_year=2024) == 1.0
    assert score_recency(2020, current_year=2024) < 1.0
    assert score_recency(2020, current_year=2024) > score_recency(2010, current_year=2024)
    assert score_recency(1800, current_year=2024) == 0.3


def test_rank_and_filter_papers():
    p1 = Paper(
        id="p1",
        title="A 0.4V 7nm Independent-Gate FinFET SRAM With Dynamic Reverse Body Biasing",
        abstract="Low-leakage standby power reduction in FinFET SRAM bitcell.",
        authors=[Author(name="Elena Rostova")],
        year=2020,
        venue="IEEE JSSC",
        citation_count=50,
        has_full_text=True,
    )
    p2 = Paper(
        id="p2",
        title="General Overview of Silicon Manufacturing",
        abstract="An introductory paper on silicon semiconductor fab lines.",
        authors=[Author(name="John Doe")],
        year=2015,
        venue="Workshop",
        citation_count=2,
        has_full_text=False,
    )

    topic = "Low-leakage SRAM using FinFET"
    ranked = rank_and_filter_papers(topic, [p1, p2], filters=MapFilters(), max_papers=10)

    # p1 ranks first; the off-topic manufacturing overview is dropped as far below the best match.
    assert ranked[0].id == "p1"
    assert [p.id for p in ranked] == ["p1"]
    assert "similarity" in ranked[0].relevance_reason


def test_rank_keeps_papers_when_all_are_on_topic():
    papers = [
        Paper(id=f"p{i}", title=f"FinFET SRAM leakage reduction study {i}", abstract="Low-leakage FinFET SRAM.", year=2020 + i)
        for i in range(4)
    ]
    ranked = rank_and_filter_papers("Low-leakage SRAM using FinFET", papers, filters=MapFilters(), max_papers=10)
    assert len(ranked) == 4


def test_rank_prefers_readable_papers_over_title_only_matches():
    # Same title and metadata; only one has anything the pipeline can read.
    common = dict(title="FinFET SRAM leakage reduction", year=2021, venue="IEEE TVLSI", citation_count=10)
    bare = Paper(id="bare", **common)
    readable = Paper(id="readable", abstract="Low-leakage FinFET SRAM bitcell with power gating.", **common)
    ranked = rank_and_filter_papers("Low-leakage SRAM using FinFET", [bare, readable], filters=MapFilters())
    assert [p.id for p in ranked] == ["readable", "bare"]
    assert "no abstract or open PDF" in ranked[1].relevance_reason


def test_rank_filters_open_access():
    p1 = Paper(id="p1", title="SRAM FinFET 1", year=2021, has_full_text=True, citation_count=5)
    p2 = Paper(id="p2", title="SRAM FinFET 2", year=2021, has_full_text=False, citation_count=5)

    filters = MapFilters(open_access_only=True)
    ranked = rank_and_filter_papers("SRAM FinFET", [p1, p2], filters=filters)
    assert len(ranked) == 1
    assert ranked[0].id == "p1"


def test_rank_filters_year_and_citations():
    p1 = Paper(id="p1", title="Paper 2023", year=2023, citation_count=10)
    p2 = Paper(id="p2", title="Paper 2017", year=2017, citation_count=10)
    p3 = Paper(id="p3", title="Paper 2022 Low Cites", year=2022, citation_count=1)

    filters = MapFilters(year_min=2020, min_citations=5)
    ranked = rank_and_filter_papers("Paper", [p1, p2, p3], filters=filters)
    assert len(ranked) == 1
    assert ranked[0].id == "p1"
