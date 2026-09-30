"""Tests for GROBID TEI XML parsing and extraction pipeline."""

import json
from pathlib import Path

import pytest

from app.clients.grobid import parse_tei_xml
from app.models.paper import Paper, PaperSections, Section
from app.pipeline.extract import (
    LlmExtractionResponse,
    _build_extraction_prompt,
    _heuristic_extract,
    _llm_to_extraction,
)

FIXTURES_DIR = Path(__file__).parent / "fixtures"


class TestGrobidTeiParsing:
    """Tests for parsing GROBID TEI XML into PaperSections."""

    @pytest.fixture
    def tei_xml(self) -> str:
        return (FIXTURES_DIR / "grobid_sample.xml").read_text()

    def test_parse_returns_sections(self, tei_xml: str):
        result = parse_tei_xml(tei_xml)
        assert isinstance(result, PaperSections)
        assert len(result.sections) > 0

    def test_abstract_extracted(self, tei_xml: str):
        result = parse_tei_xml(tei_xml)
        abstract_sections = [s for s in result.sections if s.heading == "Abstract"]
        assert len(abstract_sections) == 1
        assert "6T FinFET SRAM" in abstract_sections[0].text

    def test_body_sections_extracted(self, tei_xml: str):
        result = parse_tei_xml(tei_xml)
        headings = [s.heading for s in result.sections]
        assert any("Introduction" in h for h in headings)
        assert any("Results" in h for h in headings)
        assert any("Conclusion" in h for h in headings)

    def test_full_text_built(self, tei_xml: str):
        result = parse_tei_xml(tei_xml)
        assert len(result.full_text) > 100
        assert "8.3 pW/cell" in result.full_text
        assert "independent-gate biasing" in result.full_text

    def test_section_text_content(self, tei_xml: str):
        result = parse_tei_xml(tei_xml)
        results_section = [s for s in result.sections if "Results" in s.heading]
        assert len(results_section) == 1
        assert "45% reduction" in results_section[0].text
        assert "0.42 ns" in results_section[0].text

    def test_empty_xml(self):
        result = parse_tei_xml("")
        assert len(result.sections) == 0
        assert result.full_text == ""

    def test_malformed_xml(self):
        result = parse_tei_xml("<not valid xml>></bad>")
        assert len(result.sections) == 0


class TestHeuristicExtraction:
    """Tests for heuristic (no-LLM) extraction fallback."""

    @pytest.fixture
    def sample_paper(self) -> Paper:
        return Paper(
            id="test_paper_1",
            title="A 6T FinFET SRAM with Sub-10pW Standby Leakage",
            year=2023,
            venue="IEEE TED",
            abstract=(
                "This paper presents a novel 6T FinFET SRAM cell design that "
                "achieves sub-10pW standby leakage power. The proposed design "
                "utilizes independent-gate biasing to reduce subthreshold leakage. "
                "Results demonstrate 8.3 pW/cell standby leakage at 0.6V, "
                "representing a 45% reduction compared to conventional designs. "
                "HSPICE simulations using BSIM-CMG models confirm the design feasibility."
            ),
            has_full_text=False,
            sections=PaperSections(
                sections=[
                    Section(
                        heading="Abstract",
                        text=(
                            "This paper presents a novel 6T FinFET SRAM cell design that "
                            "achieves sub-10pW standby leakage power. The proposed design "
                            "utilizes independent-gate biasing to reduce subthreshold leakage. "
                            "Results demonstrate 8.3 pW/cell standby leakage at 0.6V, "
                            "representing a 45% reduction compared to conventional designs. "
                            "HSPICE simulations using BSIM-CMG models confirm the design feasibility."
                        ),
                    )
                ],
                full_text=(
                    "Abstract\n"
                    "This paper presents a novel 6T FinFET SRAM cell design that "
                    "achieves sub-10pW standby leakage power. The proposed design "
                    "utilizes independent-gate biasing to reduce subthreshold leakage. "
                    "Results demonstrate 8.3 pW/cell standby leakage at 0.6V, "
                    "representing a 45% reduction compared to conventional designs. "
                    "HSPICE simulations using BSIM-CMG models confirm the design feasibility."
                ),
            ),
        )

    def test_problem_not_duplicated_from_method(self, sample_paper: Paper):
        # The abstract opens with the method sentence, so it is not also reported as the problem.
        extraction = _heuristic_extract(sample_paper)
        assert extraction.method is not None
        assert extraction.problem is None or extraction.problem.text != extraction.method.text

    def test_extracts_method(self, sample_paper: Paper):
        extraction = _heuristic_extract(sample_paper)
        assert extraction.method is not None
        assert "propos" in extraction.method.text.lower() or "present" in extraction.method.text.lower()

    def test_extracts_metrics(self, sample_paper: Paper):
        extraction = _heuristic_extract(sample_paper)
        found = {(m.name, m.value, m.unit) for m in extraction.metrics}
        assert ("standby leakage", 8.3, "pW/cell") in found
        # Every metric value literally appears in its quote.
        for m in extraction.metrics:
            assert f"{m.value:g}" in m.evidence.quote

    def test_ignores_years_and_figure_numbers(self):
        text = (
            "Abstract. Since 2019 many works studied this. As shown in Fig. 3, the model "
            "achieves an accuracy of 91.2% on ImageNet and reduces latency by 35% compared with the baseline. "
            "Table 2 lists 1 A configurations used in 1990s literature."
        )
        paper = Paper(id="px", title="t", year=2024, abstract=text)
        extraction = _heuristic_extract(paper)
        found = {(m.name, m.value, m.unit) for m in extraction.metrics}
        assert ("accuracy", 91.2, "%") in found
        assert ("latency reduction", 35.0, "%") in found
        assert all(m.value not in (2019, 3, 2, 1, 1990) for m in extraction.metrics)
        assert [d.name for d in extraction.datasets] == ["ImageNet"]

    def test_tool_matching_is_whole_word(self):
        paper = Paper(
            id="py", title="t", year=2024,
            abstract="This leads to better heads. We used PyTorch for training and gaussian noise.",
        )
        names = [t.name for t in _heuristic_extract(paper).tools]
        assert names == ["PyTorch"]

    def test_extracts_tools(self, sample_paper: Paper):
        extraction = _heuristic_extract(sample_paper)
        tool_names = [t.name for t in extraction.tools]
        assert "HSPICE" in tool_names

    def test_extracts_findings(self, sample_paper: Paper):
        extraction = _heuristic_extract(sample_paper)
        assert len(extraction.findings) > 0

    def test_evidence_present(self, sample_paper: Paper):
        extraction = _heuristic_extract(sample_paper)
        if extraction.problem:
            assert extraction.problem.evidence.quote != ""
        for finding in extraction.findings:
            assert finding.evidence.quote != ""


class TestLlmToExtraction:
    """Tests for converting LLM response to internal extraction model."""

    @pytest.fixture
    def llm_response(self) -> LlmExtractionResponse:
        raw = json.loads((FIXTURES_DIR / "llm_extraction_response.json").read_text())
        return LlmExtractionResponse.model_validate(raw)

    def test_conversion_produces_extraction(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert extraction.paper_id == "paper_001"

    def test_problem_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert extraction.problem is not None
        assert "leakage" in extraction.problem.text.lower()

    def test_method_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert extraction.method is not None

    def test_technology_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert extraction.technology is not None
        assert extraction.technology.node_nm == 7
        assert extraction.technology.device == "FinFET"
        assert extraction.technology.cell_type == "6T"

    def test_metrics_converted_with_normalization(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert len(extraction.metrics) == 4

        leakage_metric = [m for m in extraction.metrics if m.name == "standby leakage power"]
        assert len(leakage_metric) == 1
        assert leakage_metric[0].value == 8.3
        assert leakage_metric[0].unit == "pW/cell"
        # Normalized: 8.3e-12 W/cell
        assert leakage_metric[0].normalized_value is not None
        assert abs(leakage_metric[0].normalized_value - 8.3e-12) < 1e-20
        assert leakage_metric[0].normalized_unit == "W/cell"

    def test_metrics_conditions(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        leakage_metric = [m for m in extraction.metrics if m.name == "standby leakage power"][0]
        assert leakage_metric.conditions.vdd == 0.6
        assert leakage_metric.conditions.temp_c == 25.0

    def test_findings_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert len(extraction.findings) == 3
        assert all(f.evidence.quote for f in extraction.findings)

    def test_tools_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert len(extraction.tools) == 1
        assert extraction.tools[0].name == "HSPICE"

    def test_limitations_converted(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        assert len(extraction.limitations) == 1

    def test_evidence_ids_generated(self, llm_response: LlmExtractionResponse):
        extraction = _llm_to_extraction("paper_001", llm_response)
        if extraction.problem:
            assert extraction.problem.evidence.id.startswith("ev_")
            assert extraction.problem.evidence.paper_id == "paper_001"


class TestExtractionPrompt:
    """Tests for extraction prompt construction."""

    def test_prompt_includes_metadata(self):
        paper = Paper(
            id="p1",
            title="Test Paper",
            year=2023,
            venue="Test Venue",
            abstract="Test abstract.",
        )
        prompt = _build_extraction_prompt(paper)
        assert "Test Paper" in prompt
        assert "2023" in prompt
        assert "Test Venue" in prompt

    def test_prompt_includes_source_text(self):
        paper = Paper(
            id="p1",
            title="Test Paper",
            year=2023,
            abstract="The abstract with important content.",
            sections=PaperSections(
                sections=[Section(heading="Body", text="Full body text here.")],
                full_text="Body\nFull body text here.",
            ),
        )
        prompt = _build_extraction_prompt(paper)
        assert "Full body text here" in prompt

    def test_prompt_truncation(self):
        paper = Paper(
            id="p1",
            title="Test Paper",
            year=2023,
            abstract="x" * 30000,
        )
        prompt = _build_extraction_prompt(paper)
        assert "[TEXT TRUNCATED]" in prompt


def test_llm_salvage_drops_only_invalid_items():
    from app.clients.llm import _salvage

    parsed = {
        "metrics": [
            {"name": "latency", "value": None, "unit": "ms", "evidence": {"quote": "q"}},
            {"name": "throughput", "value": 1200, "unit": "tok/s", "evidence": {"quote": "q"}},
        ],
        "findings": [{"text": "ok", "evidence": {"quote": "q"}}, {"text": "missing evidence"}],
    }
    result = _salvage(parsed, LlmExtractionResponse)
    assert [m.name for m in result.metrics] == ["throughput"]
    assert [f.text for f in result.findings] == ["ok"]


@pytest.mark.asyncio
async def test_full_texts_are_read_individually_and_abstracts_in_batches(monkeypatch):
    """Full-text papers get their own LLM read; abstract-only papers share batched
    requests; papers with no text use neither."""
    from app.models.paper import Paper, PaperExtraction, PaperSections
    from app.pipeline import extract as extract_mod

    individual: list[str] = []
    batches: list[list[str]] = []
    presets: dict[str, bool] = {}

    async def fake_single(paper, llm=None, preset=None):
        if llm is not None:
            individual.append(paper.id)
        presets[paper.id] = preset is not None
        return paper, extract_mod.VerificationReport(paper_id=paper.id)

    async def fake_batch(batch, llm):
        batches.append([p.id for p in batch])
        return {p.id: PaperExtraction(paper_id=p.id, extracted_by="llm:stub") for p in batch}

    class _Configured:
        model = "stub"
        def is_configured(self):
            return True
        def describe(self):
            return "stub"
        async def close(self):
            pass

    monkeypatch.setattr(extract_mod, "extract_single_paper", fake_single)
    monkeypatch.setattr(extract_mod, "_extract_abstract_batch", fake_batch)
    monkeypatch.setattr(extract_mod, "LlmClient", _Configured)
    monkeypatch.setattr(extract_mod.settings, "llm_max_papers", 8)
    monkeypatch.setattr(extract_mod, "ABSTRACT_BATCH_SIZE", 2)

    papers = [
        Paper(id="no_text", title="Top ranked but unreadable", year=2020),
        Paper(id="abstract_a", title="A", year=2020, abstract="An abstract long enough to read."),
        Paper(id="abstract_b", title="B", year=2020, abstract="Another abstract long enough to read."),
        Paper(id="abstract_c", title="C", year=2020, abstract="A third abstract long enough to read."),
        Paper(id="full", title="F", year=2020, has_full_text=True, sections=PaperSections(full_text="Full text body.")),
    ]
    out, _ = await extract_mod.extract(papers)
    assert [p.id for p in out] == [p.id for p in papers]  # input order kept
    assert individual == ["full"]
    assert batches == [["abstract_a", "abstract_b"], ["abstract_c"]]
    assert presets == {"no_text": False, "full": False, "abstract_a": True, "abstract_b": True, "abstract_c": True}


@pytest.mark.asyncio
async def test_abstract_batch_maps_results_to_the_right_papers(monkeypatch):
    from app.models.paper import Paper
    from app.pipeline import extract as extract_mod

    a = Paper(id="pa", title="A", year=2021, abstract="The electrolyte reached an ionic conductivity of 7.4 × 10−4 S cm−1 at 25 °C.")
    b = Paper(id="pb", title="B", year=2022, abstract="We find that coatings suppress dendrites.")

    class _Llm:
        model = "stub"
        last_model = "stub"
        async def structured(self, prompt, schema):
            text = prompt(20000)
            assert "[P1] A (2021)" in text and "[P2] B (2022)" in text
            return schema.model_validate({"papers": [
                {"paper": "P1", "metrics": [{
                    "name": "Ionic conductivity of the garnet electrolyte", "value": 7.4, "unit": "× 10−4 S cm−1",
                    "conditions": {"temp_c": 25}, "evidence": {"quote": "an ionic conductivity of 7.4 × 10−4 S cm−1 at 25 °C"},
                }]},
                {"paper": "[P2]", "findings": [{"text": "Coatings suppress dendrites.", "evidence": {"quote": "coatings suppress dendrites"}}]},
                {"paper": "P9", "findings": [{"text": "Unknown key is ignored.", "evidence": {"quote": "x"}}]},
            ]})

    out = await extract_mod._extract_abstract_batch([a, b], _Llm())
    assert set(out) == {"pa", "pb"}
    metric = out["pa"].metrics[0]
    assert (metric.name, metric.subject) == ("Ionic conductivity", "the garnet electrolyte")
    assert metric.unit.replace("−", "-") == "S cm-1"
    assert abs(metric.value - 7.4e-4) < 1e-12 and metric.normalized_unit == "S/cm"
    assert out["pb"].findings[0].text == "Coatings suppress dendrites."

    paper, report = await extract_mod.extract_single_paper(a, preset=out["pa"])
    assert paper.extraction.metrics and report.dropped_items == 0  # the scientific-notation value verifies
