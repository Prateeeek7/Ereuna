"""Metrics line up across papers, scientific notation is read correctly, and
conflicts are only reported between comparable, verified items."""

import pytest

from app.models.paper import Evidence, Finding, Metric, MetricConditions, Paper, PaperExtraction
from app.pipeline.analyze import detect_contradictions, llm_conflicts
from app.pipeline.evidence import value_in_quote
from app.pipeline.extract import _heuristic_metrics
from app.pipeline.metric_names import metric_key, split_metric_name, structure_metric
from app.pipeline.units import canonical_unit, fold_scientific, normalize_metric


# ── names ────────────────────────────────────────────────


@pytest.mark.parametrize("name, quantity, qualifier", [
    ("capacity retention of SSLB after 20 cycles", "capacity retention", "SSLB after 20 cycles"),
    ("Critical current density of LiF-rich LPS", "Critical current density", "LiF-rich LPS"),
    ("interfacial resistance (Li | garnet SSE)", "interfacial resistance", "Li | garnet SSE"),
    ("Power conversion efficiency (PCE)", "Power conversion efficiency", ""),
    ("Water required per ton of lithium", "Water required per ton of lithium", ""),
    ("state of charge at failure", "state of charge", "at failure"),
    ("top-1 accuracy on ImageNet", "top-1 accuracy", "ImageNet"),
])
def test_metric_names_split_into_quantity_and_qualifier(name, quantity, qualifier):
    assert split_metric_name(name) == (quantity, qualifier)


def test_conditions_and_subjects_go_to_the_right_place():
    assert structure_metric("Coulombic efficiency after initial cycles") == ("Coulombic efficiency", "", "after initial cycles")
    assert structure_metric("ionic conductivity of LLZO", "", "25 °C") == ("ionic conductivity", "LLZO", "25 °C")
    assert structure_metric("ionic conductivity", "LLZO", None) == ("ionic conductivity", "LLZO", None)


def test_same_quantity_gets_the_same_key():
    assert metric_key("Coulombic efficiency (CE)") == metric_key("coulombic efficiency of the cell") == "coulombic efficiency"
    assert metric_key("CE") == "coulombic efficiency"
    assert metric_key("Li-ion conductivity") == metric_key("Ionic conductivity at 25 °C") == "ionic conductivity"


# ── units and numbers ────────────────────────────────────


@pytest.mark.parametrize("unit, canonical", [
    ("S cm−1", "S/cm"), ("S cm^-1", "S/cm"), ("mA cm-2", "mA/cm²"), ("mAh g⁻¹", "mAh/g"),
    ("ohm·cm2", "Ω·cm²"), ("Wh kg−1", "Wh/kg"), ("mS/cm", "mS/cm"), ("%", "%"), ("tokens/s", "tokens/s"),
])
def test_units_have_one_spelling(unit, canonical):
    assert canonical_unit(unit) == canonical


def test_equal_quantities_normalize_to_the_same_value():
    assert normalize_metric(0.74, "mS cm−1") == normalize_metric(7.4e-4, "S/cm") == (0.00074, "S/cm")
    assert fold_scientific(7.4, "× 10−4 S cm−1") == (pytest.approx(7.4e-4), "S cm-1")


@pytest.mark.parametrize("value, quote, found", [
    (7.4e-4, "an ionic conductivity of 7.4 × 10−4 S cm−1", True),
    (7.4e-4, "7.4×10^-4 S/cm", True),
    (7.4e-4, "7.4e-4 S/cm", True),
    (1e-4, "about 10⁻⁴ S/cm", True),
    (7.4e-3, "an ionic conductivity of 7.4 × 10−4 S cm−1", False),  # wrong power of ten
    (8.3, "8.30 pW", True),
    (1000, "10 3 samples", False),
])
def test_values_are_found_in_quotes_in_any_notation(value, quote, found):
    assert value_in_quote(value, quote) is found


def test_text_rules_read_scientific_notation_and_compound_units():
    text = (
        "The garnet shows an ionic conductivity of 7.4 × 10−4 S cm−1 at 25 °C. The cell delivered a specific "
        "capacity of 130 mAh g−1 with a critical current density of 2.0 mA cm−2."
    )
    got = {(m.name, m.value, m.unit) for m in _heuristic_metrics("p", text)}
    assert got == {
        ("ionic conductivity", 0.00074, "S/cm"),
        ("specific capacity", 130.0, "mAh/g"),
        ("critical current density", 2.0, "mA/cm²"),
    }


# ── conflicts ────────────────────────────────────────────


def _paper(pid, metrics=(), findings=(), full=False):
    return Paper(id=pid, title=pid, year=2020, short_label=pid.upper(), has_full_text=full,
                 extraction=PaperExtraction(paper_id=pid, metrics=list(metrics), findings=list(findings)))


def _metric(name, value, unit, subject="", other=None):
    return Metric(name=name, value=value, unit=unit, subject=subject, normalized_value=None, normalized_unit=None,
                  conditions=MetricConditions(other=other), evidence=Evidence(quote=f"{name} {value} {unit}"))


def test_different_materials_are_not_a_conflict():
    papers = [
        _paper("a", [_metric("ionic conductivity", 1e-3, "S/cm", subject="LLZO garnet", other="25 °C")]),
        _paper("b", [_metric("ionic conductivity", 1e-5, "S/cm", subject="PEO polymer", other="25 °C")]),
    ]
    assert detect_contradictions(papers) == []


def test_same_material_far_apart_is_a_conflict_with_quotes():
    papers = [
        _paper("a", [_metric("ionic conductivity of LLZO", 1e-3, "S/cm", other="25 °C")]),
        _paper("b", [_metric("Ionic conductivity", 1e-4, "S cm−1", subject="LLZO", other="25 °C")]),
    ]
    # Names were over-specific; structure them the way extraction does.
    for p in papers:
        m = p.extraction.metrics[0]
        m.name, m.subject, m.conditions.other = structure_metric(m.name, m.subject, m.conditions.other)
        m.normalized_value, m.normalized_unit = normalize_metric(m.value, m.unit)
    [c] = detect_contradictions(papers)
    assert c.kind == "value" and {e.paper_id for e in c.entries} == {"a", "b"}
    assert all(e.quote for e in c.entries) and all(e.subject == "LLZO" for e in c.entries)


class _Llm:
    def __init__(self, payload):
        self.payload = payload
        self.prompt = ""

    async def structured(self, prompt, schema):
        self.prompt = prompt
        return schema.model_validate(self.payload)


@pytest.mark.asyncio
async def test_llm_conflicts_keep_only_valid_cross_paper_citations():
    papers = [
        _paper("a", [_metric("ionic conductivity", 1e-3, "S/cm", "LLZO")],
               [Finding(id="fa", text="Coatings suppress dendrites.", evidence=Evidence(quote="qa"))], full=True),
        _paper("b", [_metric("ionic conductivity", 2e-4, "S/cm", "LLZO")],
               [Finding(id="fb", text="Coatings do not suppress dendrites.", evidence=Evidence(quote="qb"))], full=True),
    ]
    llm = _Llm({"conflicts": [
        {"topic": "LLZO conductivity", "item_ids": ["M1", "M2"], "explanation": "A reports 1e-3, B 2e-4 for LLZO."},
        {"topic": "Dendrite suppression", "item_ids": ["[F1]", "F2"], "explanation": "Opposite claims about coatings."},
        {"topic": "Same paper only", "item_ids": ["M1", "F1"], "explanation": "Both from A."},
        {"topic": "Made-up ids", "item_ids": ["M9", "F7"], "explanation": "Not in the list."},
    ]})
    result = await llm_conflicts(papers, "solid electrolytes", llm)
    assert "M1 [A] ionic conductivity" in llm.prompt and "F2 [B] Coatings do not suppress dendrites." in llm.prompt
    assert [(c.kind, c.metric) for c in result] == [("value", "LLZO conductivity"), ("claim", "Dendrite suppression")]
    assert [e.quote for e in result[1].entries] == ["qa", "qb"]
    assert result[0].entries[0].value == 1e-3 and result[0].confidence == "MED"


@pytest.mark.asyncio
async def test_llm_conflicts_empty_is_a_normal_answer():
    papers = [_paper("a", findings=[Finding(id="f", text="x", evidence=Evidence(quote="q"))]),
              _paper("b", findings=[Finding(id="g", text="y", evidence=Evidence(quote="q"))])]
    assert await llm_conflicts(papers, "t", _Llm({"conflicts": []})) == []
