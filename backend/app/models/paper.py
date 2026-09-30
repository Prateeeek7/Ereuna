"""Paper, extraction, and evidence models."""

from pydantic import BaseModel, Field, computed_field


class Author(BaseModel):
    """Paper author."""

    name: str
    affiliation: str | None = None


class Evidence(BaseModel):
    """A verbatim quoted passage from a paper."""

    id: str = ""
    paper_id: str = ""
    quote: str
    section: str = ""
    page: int | None = None


class ExtractedField(BaseModel):
    """A text field with supporting evidence."""

    text: str
    evidence: Evidence


class MetricConditions(BaseModel):
    """Experimental conditions for a metric measurement."""

    vdd: float | None = None
    temp_c: float | None = None
    corner: str | None = None
    # Any other stated condition, in the paper's words ("after 20 cycles, 0.1 C", "on ImageNet").
    other: str | None = None


class Metric(BaseModel):
    """An extracted numeric metric with conditions and evidence."""

    name: str  # the measured quantity only ("ionic conductivity"), comparable across papers
    value: float
    unit: str
    # What the value describes when the paper reports several ("LiF-coated LPS", "8T cell").
    subject: str = ""
    normalized_value: float | None = None
    normalized_unit: str | None = None
    conditions: MetricConditions = Field(default_factory=MetricConditions)
    evidence: Evidence
    confidence: str = "HIGH"  # HIGH, MED, LOW

    @computed_field  # type: ignore[prop-decorator]
    @property
    def key(self) -> str:
        """Comparison key for the quantity: equal for the same quantity across papers
        ("Coulombic efficiency (CE)" and "CE" -> "coulombic efficiency")."""
        from app.pipeline.metric_names import metric_key

        return metric_key(self.name)

    @computed_field  # type: ignore[prop-decorator]
    @property
    def canonical_unit(self) -> str:
        """The unit in one spelling ("S cm−1" -> "S/cm"), for labelling and grouping."""
        from app.pipeline.units import canonical_unit

        return canonical_unit(self.unit)


class Finding(BaseModel):
    """A key finding extracted from one or more papers."""

    id: str
    text: str
    theme: str = ""
    paper_ids: list[str] = Field(default_factory=list)
    evidence: Evidence
    confidence: str = "HIGH"  # HIGH, MED, LOW


class Limitation(BaseModel):
    """A limitation identified in a paper."""

    text: str
    evidence: Evidence
    confidence: str = "HIGH"


class ToolRef(BaseModel):
    """A tool or simulator referenced in a paper."""

    name: str
    category: str = ""  # simulator, pdk, hardware
    evidence: Evidence


class DatasetRef(BaseModel):
    """A dataset or benchmark referenced in a paper."""

    name: str
    category: str = ""  # dataset, benchmark
    evidence: Evidence


class Technology(BaseModel):
    """Technology details extracted from a paper."""

    node_nm: int | None = None
    device: str | None = None
    cell_type: str | None = None
    evidence: Evidence


class PaperExtraction(BaseModel):
    """Structured extraction from a single paper."""

    paper_id: str
    problem: ExtractedField | None = None
    method: ExtractedField | None = None
    technology: Technology | None = None
    tools: list[ToolRef] = Field(default_factory=list)
    datasets: list[DatasetRef] = Field(default_factory=list)
    metrics: list[Metric] = Field(default_factory=list)
    findings: list[Finding] = Field(default_factory=list)
    limitations: list[Limitation] = Field(default_factory=list)
    # "llm:<model>" or "heuristic" — shown to users so they know how items were produced.
    extracted_by: str = ""


class Section(BaseModel):
    """A section of a parsed academic paper."""

    heading: str = ""
    text: str = ""
    page: int | None = None


class PaperSections(BaseModel):
    """Parsed full-text sections from GROBID or PyMuPDF."""

    sections: list[Section] = Field(default_factory=list)
    full_text: str = ""  # concatenated section texts for evidence search

    def build_full_text(self) -> str:
        """Concatenate all sections into searchable full text."""
        parts = []
        for s in self.sections:
            if s.heading:
                parts.append(s.heading)
            if s.text:
                parts.append(s.text)
        self.full_text = "\n".join(parts)
        return self.full_text


class VerificationReport(BaseModel):
    """Report from evidence verification of an extraction."""

    paper_id: str = ""
    total_items: int = 0
    verified_items: int = 0
    dropped_items: int = 0
    drop_rate: float = 0.0


class Paper(BaseModel):
    """A research paper with metadata and optional extraction."""

    id: str
    doi: str | None = None
    title: str
    authors: list[Author] = Field(default_factory=list)
    year: int
    venue: str = ""
    citation_count: int = 0
    abstract: str = ""
    oa_pdf_url: str | None = None
    has_full_text: bool = False
    relevance_score: float = 0.0
    relevance_reason: str = ""
    short_label: str = ""
    source: str = "search"  # "upload" for a PDF the user provided
    sections: PaperSections | None = None
    extraction: PaperExtraction | None = None

