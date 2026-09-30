"""Analysis models: gaps, contradictions, experiments."""

from pydantic import BaseModel, Field


class Gap(BaseModel):
    """An identified research gap with supporting evidence."""

    id: str  # e.g. "G1"
    statement: str
    pattern: str  # e.g. "0 of 24 papers test below 0.5 V"
    supporting_paper_ids: list[str] = Field(default_factory=list)
    why_it_matters: str = ""
    confidence: str = "HIGH"  # HIGH, MED, LOW


class ContradictionEntry(BaseModel):
    """One side of a contradiction."""

    paper_id: str
    value: float
    unit: str
    conditions: dict[str, float | str | None] = Field(default_factory=dict)


class Contradiction(BaseModel):
    """A contradiction where the same metric disagrees across papers."""

    id: str
    metric: str
    entries: list[ContradictionEntry] = Field(default_factory=list)
    likely_reason: str = ""
    confidence: str = "HIGH"  # HIGH, MED, LOW


class Experiment(BaseModel):
    """A suggested experiment to address a research gap."""

    id: str
    gap_id: str
    hypothesis: str
    setup: str = ""
    tools: list[str] = Field(default_factory=list)
    variables: list[str] = Field(default_factory=list)
    expected_result: str = ""
    difficulty: str = "MED"  # LOW, MED, HIGH
    paper_ids: list[str] = Field(default_factory=list)
