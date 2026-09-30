"""Citation graph models."""

from pydantic import BaseModel


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
    """An edge in the citation graph."""

    source: str
    target: str
    weight: float = 1.0
