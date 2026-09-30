"""Tests for Phase 4 Analysis features: Stage 6 cross-paper, Stage 7 graph, Stage 8 experiments, and exports."""

import asyncio

import pytest
from fastapi.testclient import TestClient

from app.api.state import state_manager
from app.main import app
from app.models.map import Gap, MapFilters, MapStats, ResearchMap
from app.models.paper import (
    Author,
    Evidence,
    Limitation,
    Metric,
    MetricConditions,
    Paper,
    PaperExtraction,
    Technology,
)
from app.pipeline.analyze import (
    aggregate_tools_and_datasets,
    corpus_synthesis,
    detect_contradictions,
    heuristic_gaps,
    llm_gaps,
)
from app.pipeline.experiments import suggest_experiments
from app.pipeline.graph import build_graph


@pytest.fixture
def sample_papers() -> list[Paper]:
    """Create a diverse set of papers with extraction data."""
    ev = Evidence(quote="Evidence quote from manuscript.", section="Results")

    p1 = Paper(
        id="p1",
        title="Sub-threshold 7nm FinFET SRAM Cell Optimization",
        authors=[Author(name="Alice Smith"), Author(name="Bob Jones")],
        year=2021,
        venue="IEEE JSSC",
        citation_count=120,
        has_full_text=True,
        short_label="Smith+21",
        extraction=PaperExtraction(
            paper_id="p1",
            technology=Technology(node_nm=7, device="FinFET", cell_type="8T", evidence=ev),
            limitations=[
                Limitation(
                    text="Process variation effects on read stability were not evaluated with Monte Carlo analysis.",
                    evidence=ev,
                )
            ],
            metrics=[
                Metric(
                    name="standby_leakage",
                    value=12.4,
                    unit="pW",
                    conditions=MetricConditions(vdd=0.4, temp_c=25.0),
                    evidence=ev,
                ),
                Metric(
                    name="read_delay",
                    value=180.0,
                    unit="ps",
                    conditions=MetricConditions(vdd=0.4, temp_c=25.0),
                    evidence=ev,
                ),
            ],
        ),
    )

    p2 = Paper(
        id="p2",
        title="High-Speed 14nm FinFET Cache Memory for Cloud Processors",
        authors=[Author(name="Charlie Brown")],
        year=2019,
        venue="IEEE ISSCC",
        citation_count=85,
        has_full_text=True,
        short_label="Brown 19",
        extraction=PaperExtraction(
            paper_id="p2",
            technology=Technology(node_nm=14, device="FinFET", cell_type="6T", evidence=ev),
            metrics=[
                Metric(
                    name="standby_leakage",
                    value=48.2,
                    unit="pW",
                    conditions=MetricConditions(vdd=0.75, temp_c=85.0),
                    evidence=ev,
                ),
                Metric(
                    name="read_delay",
                    value=95.0,
                    unit="ps",
                    conditions=MetricConditions(vdd=0.75, temp_c=25.0),
                    evidence=ev,
                ),
            ],
        ),
    )

    p3 = Paper(
        id="p3",
        title="Near-Threshold FinFET Operating Limits",
        authors=[Author(name="David Wilson")],
        year=2018,
        venue="IEEE TCAD",
        citation_count=40,
        has_full_text=False,
        short_label="Wilson 18",
        extraction=PaperExtraction(
            paper_id="p3",
            technology=Technology(node_nm=10, device="FinFET", cell_type="6T", evidence=ev),
            limitations=[
                Limitation(
                    text="Read stability under process variation was not evaluated in this work.",
                    evidence=ev,
                ),
                Limitation(text="Only simulation results are reported; no silicon measurements.", evidence=ev),
            ],
            metrics=[
                Metric(
                    name="standby_leakage",
                    value=28.0,
                    unit="pW",
                    conditions=MetricConditions(vdd=0.6, temp_c=25.0),
                    evidence=ev,
                )
            ],
        ),
    )

    return [p1, p2, p3]


class _StubOpenAlex:
    """Returns canned OpenAlex records so the graph test does not hit the network."""

    def __init__(self, records: dict[str, list[dict]]):
        self.records = records
        self.calls: list[str] = []

    async def filter_works(self, filter_expr: str, select: str, per_page: int = 50) -> list[dict]:
        self.calls.append(filter_expr)
        field, values = filter_expr.split(":", 1)
        out = []
        for v in values.split("|"):
            out.extend(self.records.get(f"{field}:{v}", []))
        return out

    async def close(self) -> None:
        pass


class _StubS2:
    def __init__(self, records):
        self.records = records

    async def batch_references(self, ids):
        by_id = {r["lookup"]: r for r in self.records}
        return [by_id.get(i) for i in ids]

    async def close(self) -> None:
        pass


class _StubLlm:
    def __init__(self, response):
        self.response = response
        self.model = "stub"
        self.provider = "stub"

    def is_configured(self) -> bool:
        return True

    async def structured(self, prompt, schema):
        return schema.model_validate(self.response)


def test_detect_contradictions(sample_papers: list[Paper]) -> None:
    """Same metric + unit reported >25% apart across papers is flagged with the differing conditions."""
    contradictions = detect_contradictions(sample_papers)
    assert len(contradictions) >= 1

    leak = next(c for c in contradictions if "leakage" in c.metric)
    assert len(leak.entries) == 3  # all three papers report standby leakage
    values = [e.value for e in leak.entries]
    assert values == sorted(values)
    assert "supply voltage" in leak.likely_reason
    assert "process node" in leak.likely_reason


def test_contradictions_require_same_unit() -> None:
    ev = Evidence(quote="q")

    def paper(pid: str, value: float, unit: str) -> Paper:
        return Paper(
            id=pid, title=pid, year=2020,
            extraction=PaperExtraction(
                paper_id=pid,
                metrics=[Metric(name="latency", value=value, unit=unit, normalized_unit=unit, evidence=ev)],
            ),
        )

    assert detect_contradictions([paper("a", 5, "ms"), paper("b", 50, "tokens")]) == []
    # Same unit but no stated conditions on either side: not comparable.
    assert detect_contradictions([paper("a", 5, "ms"), paper("b", 50, "ms")]) == []
    a, b = paper("a", 5, "ms"), paper("b", 50, "ms")
    for pp in (a, b):
        pp.extraction.metrics[0].conditions.corner = "batch size 1"
    result = detect_contradictions([a, b])
    assert len(result) == 1
    assert "same stated conditions (batch size 1)" in result[0].likely_reason


def test_relative_results_are_not_compared() -> None:
    ev = Evidence(quote="q")
    papers = [
        Paper(id=pid, title=pid, year=2020, extraction=PaperExtraction(paper_id=pid, metrics=[
            Metric(name="inference speedup", value=v, unit="×", conditions=MetricConditions(corner="A100"), evidence=ev),
            Metric(name="latency reduction", value=v * 10, unit="%", conditions=MetricConditions(corner="A100"), evidence=ev),
        ]))
        for pid, v in (("a", 1.2), ("b", 3.7))
    ]
    assert detect_contradictions(papers) == []


def test_heuristic_gaps_come_from_stated_limitations(sample_papers: list[Paper]) -> None:
    import re

    gaps = heuristic_gaps(sample_papers)
    assert len(gaps) >= 1
    all_limitations = {
        lim.text for p in sample_papers if p.extraction for lim in p.extraction.limitations
    }
    pattern_re = re.compile(r"\d+\s+of\s+\d+\s+papers", re.IGNORECASE)
    for gap in gaps:
        assert gap.statement in all_limitations  # verbatim, never templated
        assert pattern_re.search(gap.pattern)
        assert gap.supporting_paper_ids

    # The two process-variation limitations (p1, p3) are grouped into one gap.
    variation_gap = gaps[0]
    assert set(variation_gap.supporting_paper_ids) == {"p1", "p3"}
    assert variation_gap.pattern.startswith("2 of 3 papers")


def test_no_gaps_without_limitations() -> None:
    p = Paper(id="x", title="x", year=2020, extraction=PaperExtraction(paper_id="x"))
    assert heuristic_gaps([p]) == []


@pytest.mark.asyncio
async def test_llm_gaps_validate_citations(sample_papers: list[Paper]) -> None:
    llm = _StubLlm({
        "gaps": [
            {"statement": "Variation-aware stability is unstudied.", "why_it_matters": "Yield.", "limitation_ids": ["L1", "L2"]},
            {"statement": "Invented gap with no support.", "why_it_matters": "n/a", "limitation_ids": ["L99"]},
        ]
    })
    gaps = await llm_gaps(sample_papers, "FinFET SRAM", llm)
    assert [g.statement for g in gaps] == ["Variation-aware stability is unstudied."]
    assert gaps[0].supporting_paper_ids == ["p1", "p3"]
    assert gaps[0].pattern.startswith("2 of 3 papers")


def test_corpus_synthesis_is_factual(sample_papers: list[Paper]) -> None:
    syn = corpus_synthesis(sample_papers, "FinFET SRAM")
    assert "3 papers" in syn.text
    assert "2018" in syn.text and "2021" in syn.text
    assert syn.citation_ids[0] == "p1"  # most cited


@pytest.mark.asyncio
async def test_build_citation_graph_uses_real_references(sample_papers: list[Paper]) -> None:
    for p, doi in zip(sample_papers, ["10.1/a", "10.1/b", "10.1/c"]):
        p.doi = doi
    stub = _StubOpenAlex({
        # p1 cites p2 and p3; p2 cites p3; p1 and p2 both cite an external work W9.
        "doi:10.1/a": [{"id": "https://openalex.org/W1", "doi": "https://doi.org/10.1/a",
                         "referenced_works": ["https://openalex.org/W2", "https://openalex.org/W3", "https://openalex.org/W9"]}],
        "doi:10.1/b": [{"id": "https://openalex.org/W2", "doi": "https://doi.org/10.1/b",
                         "referenced_works": ["https://openalex.org/W3", "https://openalex.org/W9"]}],
        "doi:10.1/c": [{"id": "https://openalex.org/W3", "doi": "https://doi.org/10.1/c", "referenced_works": []}],
        "openalex:W9": [{"id": "https://openalex.org/W9", "title": "Seminal work", "publication_year": 2003,
                          "cited_by_count": 900, "authorships": [{"author": {"display_name": "Grace Hopper"}}]}],
    })
    graph = await build_graph(sample_papers, openalex_client=stub)

    edges = {(e.source, e.target) for e in graph.edges}
    assert ("p1", "p2") in edges and ("p1", "p3") in edges and ("p2", "p3") in edges
    assert ("p1", "oa_W9") in edges and ("p2", "oa_W9") in edges
    assert len(edges) == 5  # nothing inferred beyond the reference lists

    external = [n for n in graph.nodes if not n.in_map]
    assert len(external) == 1
    assert external[0].label == "Hopper '03"
    assert external[0].citation_count == 900

    for node in graph.nodes:
        assert 30.0 <= node.x <= 970.0
        assert 30.0 <= node.y <= 970.0
        assert 8.0 <= node.size <= 28.0


@pytest.mark.asyncio
async def test_graph_without_resolved_references_has_no_edges(sample_papers: list[Paper]) -> None:
    graph = await build_graph(sample_papers, openalex_client=_StubOpenAlex({}), s2_client=_StubS2([]))
    assert len(graph.nodes) == len(sample_papers)
    assert graph.edges == []


@pytest.mark.asyncio
async def test_graph_falls_back_to_semantic_scholar(sample_papers: list[Paper]) -> None:
    for p, doi in zip(sample_papers, ["10.1/a", "10.1/b", "10.1/c"]):
        p.doi = doi
    ext = {"paperId": "EXT", "title": "Shared foundation", "year": 1999, "citationCount": 50,
           "authors": [{"name": "Ada Lovelace"}]}
    s2 = _StubS2([
        {"lookup": "DOI:10.1/a", "paperId": "A", "references": [{"paperId": "B"}, ext]},
        {"lookup": "DOI:10.1/b", "paperId": "B", "references": [ext]},
        {"lookup": "DOI:10.1/c", "paperId": "C", "references": []},
    ])
    graph = await build_graph(sample_papers, openalex_client=_StubOpenAlex({}), s2_client=s2)
    edges = {(e.source, e.target) for e in graph.edges}
    assert edges == {("p1", "p2"), ("p1", "s2_EXT"), ("p2", "s2_EXT")}
    ext_node = next(n for n in graph.nodes if not n.in_map)
    assert ext_node.label == "Lovelace '99"


@pytest.mark.asyncio
async def test_no_experiments_without_llm(sample_papers: list[Paper]) -> None:
    gaps = heuristic_gaps(sample_papers)
    assert await suggest_experiments(gaps, sample_papers) == []


@pytest.mark.asyncio
async def test_llm_experiments_linked_to_gaps(sample_papers: list[Paper]) -> None:
    gaps = heuristic_gaps(sample_papers)
    llm = _StubLlm({
        "experiments": [
            {"gap_id": gaps[0].id, "hypothesis": "Monte Carlo analysis will reveal read-stability loss.",
             "setup": "Run 1000-point Monte Carlo.", "tools": ["HSPICE"], "variables": ["sigma Vt"],
             "expected_result": "Distribution of read SNM.", "difficulty": "med"},
            {"gap_id": "gap_404", "hypothesis": "Unlinked", "setup": "", "expected_result": "", "difficulty": "LOW"},
        ]
    })
    experiments = await suggest_experiments(gaps, sample_papers, topic="SRAM", llm=llm)
    assert len(experiments) == 1
    assert experiments[0].gap_id == gaps[0].id
    assert experiments[0].difficulty == "MED"
    assert experiments[0].paper_ids == gaps[0].supporting_paper_ids[:3]


def test_graph_and_export_endpoints(sample_papers: list[Paper]) -> None:
    """Test GET /v1/maps/{id}/graph and POST /v1/maps/{id}/export."""
    client = TestClient(app)

    # Store a test map in state_manager
    map_id = "test_map_phase4"
    gaps = heuristic_gaps(sample_papers)
    contradictions = detect_contradictions(sample_papers)
    test_map = ResearchMap(
        id=map_id,
        topic="Low-leakage FinFET SRAM",
        normalized_topic="low leakage finfet sram",
        filters=MapFilters(),
        stats=MapStats(total_papers=len(sample_papers), gaps_count=len(gaps), contradictions_count=len(contradictions)),
        papers=sample_papers,
        gaps=gaps,
        contradictions=contradictions,
    )
    from app.models.map import GraphData
    test_map.graph = GraphData(nodes=[], edges=[])
    asyncio.run(state_manager.store_map(test_map))

    # 1. Test Graph Endpoint
    resp_graph = client.get(f"/v1/maps/{map_id}/graph")
    assert resp_graph.status_code == 200
    graph_data = resp_graph.json()
    assert "nodes" in graph_data
    assert "edges" in graph_data

    # 2. Test Export Endpoint - Markdown
    resp_export_md = client.post(f"/v1/maps/{map_id}/export", json={"format": "md"})
    assert resp_export_md.status_code == 200
    data_md = resp_export_md.json()
    assert data_md["format"] == "md"
    assert "/download?format=md" in data_md["download_url"]

    # 3. Test Download Endpoint - Markdown
    resp_dl_md = client.get(f"/v1/maps/{map_id}/download?format=md")
    assert resp_dl_md.status_code == 200
    assert "# Research Map: Low-leakage FinFET SRAM" in resp_dl_md.text
    assert "## Research Gaps" in resp_dl_md.text
    assert "## Divergent Results" in resp_dl_md.text
    assert "Nonenm" not in resp_dl_md.text

    # 4. Test Download Endpoint - BibTeX
    resp_dl_bib = client.get(f"/v1/maps/{map_id}/download?format=bibtex")
    assert resp_dl_bib.status_code == 200
    assert "@article{smith2021" in resp_dl_bib.text
    keys = [line.split("{")[1].rstrip(",") for line in resp_dl_bib.text.splitlines() if line.startswith("@")]
    assert len(keys) == len(set(keys)) == len(sample_papers)

    # 5. Test Download Endpoint - CSV
    resp_dl_csv = client.get(f"/v1/maps/{map_id}/download?format=csv")
    assert resp_dl_csv.status_code == 200
    assert "id,short_label,title" in resp_dl_csv.text


@pytest.mark.asyncio
async def test_llm_synthesis_resolves_all_citation_formats(sample_papers: list[Paper]) -> None:
    from app.models.paper import Finding
    from app.pipeline.analyze import llm_synthesis

    ev = Evidence(quote="q")
    for p in sample_papers:
        p.extraction.findings = [Finding(id=f"f_{p.id}", text=f"finding of {p.id}", evidence=ev)]
    llm = _StubLlm({
        "text": "Leakage drops sharply (F1, F2). Delay also improves [F3]; see also (F9).",
        "finding_ids": ["F1"],
    })
    syn = await llm_synthesis(sample_papers, "SRAM", llm)
    assert "F1" not in syn.text and "F9" not in syn.text
    assert "[Smith+21; Brown 19]" in syn.text
    assert "[Wilson 18]" in syn.text
    assert syn.citation_ids == ["p1", "p2", "p3"]


@pytest.mark.asyncio
async def test_llm_synthesis_expands_citation_ranges(sample_papers: list[Paper]) -> None:
    from app.models.paper import Finding
    from app.pipeline.analyze import llm_synthesis

    ev = Evidence(quote="q")
    for p in sample_papers:
        p.extraction.findings = [Finding(id=f"f_{p.id}", text=f"finding of {p.id}", evidence=ev)]
    # Non-breaking hyphen range (as models emit), an en-dash range, and a range of unknown ids.
    llm = _StubLlm({
        "text": "Wider fins raise leakage (F1‑F2), stacking helps [F2–F3], and more (F8-F9).",
        "finding_ids": [],
    })
    syn = await llm_synthesis(sample_papers, "SRAM", llm)
    assert "(‑)" not in syn.text and "()" not in syn.text and "F" not in syn.text.replace("Wider", "")
    assert "raise leakage [Smith+21; Brown 19]" in syn.text
    assert "stacking helps [Brown 19; Wilson 18]" in syn.text
    assert syn.text.endswith("and more.")
    assert syn.citation_ids == ["p1", "p2", "p3"]
