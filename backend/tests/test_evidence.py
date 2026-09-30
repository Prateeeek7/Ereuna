"""Tests for evidence verification."""

from app.models.paper import (
    DatasetRef,
    Evidence,
    ExtractedField,
    Finding,
    Limitation,
    Metric,
    MetricConditions,
    PaperExtraction,
    ToolRef,
)
from app.pipeline.evidence import verify_evidence, verify_and_filter, _normalize_text


class TestNormalizeText:
    """Tests for text normalization."""

    def test_whitespace_collapse(self):
        assert _normalize_text("hello   world") == "hello world"

    def test_newlines_to_space(self):
        assert _normalize_text("hello\n\nworld") == "hello world"

    def test_tabs_to_space(self):
        assert _normalize_text("hello\t\tworld") == "hello world"

    def test_lowercase(self):
        assert _normalize_text("Hello WORLD") == "hello world"

    def test_typographic_quotes(self):
        assert _normalize_text("\u201chello\u201d") == '"hello"'

    def test_soft_hyphen_removal(self):
        assert _normalize_text("sub\u00adthreshold") == "subthreshold"

    def test_hyphenated_linebreak(self):
        assert _normalize_text("sub-\nthreshold") == "subthreshold"

    def test_em_dash_to_hyphen(self):
        assert _normalize_text("a\u2014b") == "a-b"

    def test_empty_string(self):
        assert _normalize_text("") == ""


class TestVerifyEvidence:
    """Tests for evidence quote verification."""

    def test_exact_match(self):
        source = "This paper presents a novel 6T FinFET SRAM cell design."
        quote = "novel 6T FinFET SRAM cell design"
        assert verify_evidence(quote, source) is True

    def test_exact_match_case_insensitive(self):
        source = "The proposed design utilizes Independent-Gate Biasing."
        quote = "the proposed design utilizes independent-gate biasing"
        assert verify_evidence(quote, source) is True

    def test_whitespace_normalization(self):
        source = "The standby   leakage\n  power is  8.3 pW/cell."
        quote = "The standby leakage power is 8.3 pW/cell."
        assert verify_evidence(quote, source) is True

    def test_soft_hyphen_in_source(self):
        source = "sub\u00adthreshold leakage is exponentially reduced"
        quote = "subthreshold leakage is exponentially reduced"
        assert verify_evidence(quote, source) is True

    def test_hyphenated_linebreak_in_source(self):
        source = "the sub-\nthreshold leakage is exponentially reduced"
        quote = "the subthreshold leakage is exponentially reduced"
        assert verify_evidence(quote, source) is True

    def test_typographic_quotes_match(self):
        source = 'The paper claims \u201csignificant improvement\u201d in leakage.'
        quote = 'The paper claims "significant improvement" in leakage.'
        assert verify_evidence(quote, source) is True

    def test_fuzzy_match_minor_difference(self):
        source = "The standby leakage power is 8.3 pW/cell at Vdd=0.6V and 25°C."
        quote = "The standby leakage power is 8.3 pW/cell at Vdd=0.6V and 25C."
        # Minor difference (° removed) should fuzzy match
        assert verify_evidence(quote, source) is True

    def test_no_match(self):
        source = "This paper presents a novel 6T FinFET SRAM cell design."
        quote = "A completely unrelated sentence about transformers."
        assert verify_evidence(quote, source) is False

    def test_empty_quote(self):
        source = "Some text."
        assert verify_evidence("", source) is False

    def test_empty_source(self):
        assert verify_evidence("quote", "") is False

    def test_both_empty(self):
        assert verify_evidence("", "") is False

    def test_very_short_quote_exact(self):
        source = "SRAM cell design with FinFET technology."
        quote = "SRAM cell"
        assert verify_evidence(quote, source) is True

    def test_very_short_quote_no_match(self):
        source = "SRAM cell design with FinFET technology."
        quote = "GAA MBCFET"
        assert verify_evidence(quote, source) is False


class TestVerifyAndFilter:
    """Tests for full extraction verification and filtering."""

    def _make_evidence(self, quote: str) -> Evidence:
        return Evidence(id="ev_test", paper_id="p1", quote=quote, section="Test")

    def test_all_verified(self):
        source_text = (
            "This paper presents a novel design. "
            "The leakage power is 8.3 pW/cell. "
            "The method uses independent-gate biasing."
        )
        extraction = PaperExtraction(
            paper_id="p1",
            problem=ExtractedField(
                text="Novel design",
                evidence=self._make_evidence("This paper presents a novel design."),
            ),
            method=ExtractedField(
                text="Independent-gate biasing",
                evidence=self._make_evidence("The method uses independent-gate biasing."),
            ),
            metrics=[
                Metric(
                    name="leakage",
                    value=8.3,
                    unit="pW/cell",
                    evidence=self._make_evidence("The leakage power is 8.3 pW/cell."),
                )
            ],
        )

        filtered, report = verify_and_filter(extraction, source_text)
        assert report.total_items == 3
        assert report.verified_items == 3
        assert report.dropped_items == 0
        assert report.drop_rate == 0.0
        assert filtered.problem is not None
        assert filtered.method is not None
        assert len(filtered.metrics) == 1

    def test_drops_unverified(self):
        source_text = "This paper presents a novel design."
        extraction = PaperExtraction(
            paper_id="p1",
            problem=ExtractedField(
                text="Novel design",
                evidence=self._make_evidence("This paper presents a novel design."),
            ),
            method=ExtractedField(
                text="Fake method",
                evidence=self._make_evidence("This quote does not exist in the text at all."),
            ),
            findings=[
                Finding(
                    id="f1",
                    text="A finding",
                    evidence=self._make_evidence("Also not in the text."),
                ),
            ],
        )

        filtered, report = verify_and_filter(extraction, source_text)
        assert report.total_items == 3
        assert report.verified_items == 1
        assert report.dropped_items == 2
        assert filtered.problem is not None
        assert filtered.method is None  # dropped
        assert len(filtered.findings) == 0  # dropped

    def test_empty_extraction(self):
        filtered, report = verify_and_filter(
            PaperExtraction(paper_id="p1"),
            "Some text",
        )
        assert report.total_items == 0
        assert report.verified_items == 0

    def test_tools_and_datasets(self):
        source_text = "We used HSPICE for simulation. The MNIST dataset was used for training."
        extraction = PaperExtraction(
            paper_id="p1",
            tools=[
                ToolRef(
                    name="HSPICE",
                    category="simulator",
                    evidence=self._make_evidence("We used HSPICE for simulation."),
                ),
                ToolRef(
                    name="FakeTool",
                    category="eda",
                    evidence=self._make_evidence("FakeTool was never mentioned."),
                ),
            ],
            datasets=[
                DatasetRef(
                    name="MNIST",
                    category="dataset",
                    evidence=self._make_evidence("The MNIST dataset was used for training."),
                ),
            ],
        )

        filtered, report = verify_and_filter(extraction, source_text)
        assert report.total_items == 3
        assert report.verified_items == 2
        assert report.dropped_items == 1
        assert len(filtered.tools) == 1
        assert filtered.tools[0].name == "HSPICE"
        assert len(filtered.datasets) == 1


def test_fuzzy_match_tolerates_wording_but_not_numbers():
    from app.pipeline.evidence import verify_evidence

    source = ("filler sentence about unrelated matters. " * 2000) + (
        "The proposed router reduces expert load imbalance by 35% while keeping accuracy "
        "within 0.2 points of the dense baseline. "
    ) + ("trailing filler text. " * 1000)
    exact = "The proposed router reduces expert load imbalance by 35% while keeping accuracy within 0.2 points of the dense baseline."
    assert verify_evidence(exact, source)
    assert verify_evidence(exact.replace("reduces expert", "reduces the expert"), source)
    assert not verify_evidence(exact.replace("35%", "53%"), source)
    assert not verify_evidence("Our router halves imbalance and improves accuracy by 3 points.", source)


def test_metric_value_must_appear_in_quote():
    from app.models.paper import Evidence, Metric, PaperExtraction
    from app.pipeline.evidence import verify_and_filter

    source = "We measure a throughput of 1,200 tokens/s on a single GPU."
    ev = Evidence(quote="We measure a throughput of 1,200 tokens/s on a single GPU.")
    extraction = PaperExtraction(
        paper_id="p",
        metrics=[
            Metric(name="throughput", value=1200, unit="tokens/s", evidence=ev),
            Metric(name="throughput", value=2400, unit="tokens/s", evidence=ev.model_copy()),
        ],
    )
    filtered, report = verify_and_filter(extraction, source)
    assert [m.value for m in filtered.metrics] == [1200]
    assert report.dropped_items == 1


def test_fuzzy_tolerates_dropped_inline_citation():
    from app.pipeline.evidence import verify_evidence

    source = ("filler text. " * 500) + (
        "The model was trained on data sampled from the\nCulturaX (Nguyen et al., 2024a) dataset, which\n"
        "consists of raw text documents in 167 languages, amounting to over 6 trillion tokens."
    ) + (" more filler." * 300)
    quote = (
        "The model was trained on data sampled from the CulturaX dataset, which consists of raw text "
        "documents in 167 languages, amounting to over 6 trillion tokens."
    )
    assert verify_evidence(quote, source)
    assert not verify_evidence(quote.replace("167", "176"), source)


def test_tool_with_reformatted_quote_gets_real_sentence():
    from app.models.paper import DatasetRef, Evidence, PaperExtraction
    from app.pipeline.evidence import verify_and_filter

    source = "We evaluate on XWinograd (Tikhonov and Ryabinin, 2021), and\nBelebele (Bandarkar et al., 2023). Other text."
    extraction = PaperExtraction(
        paper_id="p",
        datasets=[
            DatasetRef(name="Belebele", evidence=Evidence(quote="Belebele – Reading task with 122 languages.")),
            DatasetRef(name="ImaginaryBench", evidence=Evidence(quote="ImaginaryBench is used.")),
        ],
    )
    filtered, report = verify_and_filter(extraction, source)
    assert [d.name for d in filtered.datasets] == ["Belebele"]
    assert "Belebele (Bandarkar et al., 2023)" in filtered.datasets[0].evidence.quote
    assert report.dropped_items == 1
