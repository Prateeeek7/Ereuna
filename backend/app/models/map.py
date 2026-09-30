"""Research map and related models."""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class MapFilters(BaseModel):
    """Filters applied when creating a research map."""

    year_min: int | None = None
    year_max: int | None = None
    fields: list[str] = Field(default_factory=list)
    min_citations: int = 0
    open_access_only: bool = False
    max_papers: int = 25


class MapStats(BaseModel):
    """Summary statistics for a research map."""

    total_papers: int = 0
    full_text_papers: int = 0
    abstract_only_papers: int = 0
    year_range: list[int] = Field(default_factory=list)
    gaps_count: int = 0
    contradictions_count: int = 0
    uploaded_papers: int = 0


class Synthesis(BaseModel):
    """Overview paragraph with citation references."""

    text: str = ""
    citation_ids: list[str] = Field(default_factory=list)


from app.models.paper import Finding, Paper


class ContradictionEntry(BaseModel):
    """One paper's side of a disagreement: a reported value, or a stated claim."""

    paper_id: str
    value: Optional[float] = None
    unit: str = ""
    subject: str = ""
    conditions: dict[str, str] = Field(default_factory=dict)
    # The paper's own words behind this side (verified quote) and where it is.
    statement: str = ""
    quote: str = ""
    section: str = ""
    page: Optional[int] = None


class Contradiction(BaseModel):
    """Papers that disagree: different values for the same quantity on a comparable
    system ("value"), or opposing claims about the same effect ("claim")."""

    id: str
    kind: str = "value"
    metric: str
    entries: list[ContradictionEntry] = Field(default_factory=list)
    likely_reason: str = ""
    confidence: str = "HIGH"


class Gap(BaseModel):
    """An identified research gap with quantified pattern."""

    id: str
    statement: str
    pattern: str
    supporting_paper_ids: list[str] = Field(default_factory=list)
    why_it_matters: str = ""
    confidence: str = "HIGH"


class Experiment(BaseModel):
    """A suggested experiment protocol targeting a research gap."""

    id: str
    gap_id: str
    hypothesis: str
    setup: str = ""
    tools: list[str] = Field(default_factory=list)
    variables: list[str] = Field(default_factory=list)
    expected_result: str = ""
    difficulty: str = "MED"
    paper_ids: list[str] = Field(default_factory=list)


class GraphNode(BaseModel):
    """A node in the citation graph."""

    paper_id: str
    x: float = 0.0
    y: float = 0.0
    size: float = 10.0
    year: int = 0
    in_map: bool = True
    label: str = ""
    citation_count: int = 0


class GraphEdge(BaseModel):
    """A directed citation edge in the citation graph."""

    source: str
    target: str
    weight: float = 1.0


class GraphData(BaseModel):
    """Precomputed citation graph with force-directed layout."""

    nodes: list[GraphNode] = Field(default_factory=list)
    edges: list[GraphEdge] = Field(default_factory=list)


class ToolEntry(BaseModel):
    """A tool, dataset, or benchmark referenced across the corpus."""

    name: str
    category: str = ""
    count: int = 0
    paper_ids: list[str] = Field(default_factory=list)


class ResearchMap(BaseModel):
    """A complete research map for a topic."""

    id: str
    topic: str
    normalized_topic: str
    filters: MapFilters = Field(default_factory=MapFilters)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)
    stats: MapStats = Field(default_factory=MapStats)
    synthesis: Synthesis = Field(default_factory=Synthesis)
    paper_ids: list[str] = Field(default_factory=list)
    papers: list[Paper] = Field(default_factory=list)
    findings: list[Finding] = Field(default_factory=list)
    gaps: list[Gap] = Field(default_factory=list)
    contradictions: list[Contradiction] = Field(default_factory=list)
    experiments: list[Experiment] = Field(default_factory=list)
    tools: list[ToolEntry] = Field(default_factory=list)
    graph: GraphData | None = None
    # "search": built from a literature search (shared, cached by topic);
    # "upload": built from the owner's PDFs (visible to the owner only).
    source: str = "search"
    owner_id: str | None = None



class CreateMapRequest(BaseModel):
    """Request body for POST /v1/maps."""

    topic: str
    filters: MapFilters = Field(default_factory=MapFilters)


class CreateMapResponse(BaseModel):
    """Response for POST /v1/maps."""

    job_id: str | None = None
    map_id: str
    status: str  # "processing" or "cached"


class RefreshMapResponse(BaseModel):
    """Response for POST /v1/maps/{id}/refresh."""

    job_id: str
    status: str = "processing"


class ExportRequest(BaseModel):
    """Request body for POST /v1/maps/{id}/export."""

    format: str  # pdf, bibtex, csv, md


class ExportResponse(BaseModel):
    """Response for POST /v1/maps/{id}/export."""

    download_url: str
    format: str
    expires_at: datetime
