"""Pydantic v2 data models for Ereuna."""

from app.models.analysis import Contradiction, ContradictionEntry, Experiment, Gap
from app.models.graph import GraphEdge, GraphNode
from app.models.job import Job, JobEvent, LogEntry
from app.models.map import MapFilters, MapStats, ResearchMap, Synthesis
from app.models.paper import (
    Author,
    DatasetRef,
    Evidence,
    ExtractedField,
    Finding,
    Limitation,
    Metric,
    MetricConditions,
    Paper,
    PaperExtraction,
    PaperSections,
    Section,
    Technology,
    ToolRef,
    VerificationReport,
)

__all__ = [
    "Author",
    "Contradiction",
    "ContradictionEntry",
    "DatasetRef",
    "Evidence",
    "Experiment",
    "ExtractedField",
    "Finding",
    "Gap",
    "GraphEdge",
    "GraphNode",
    "Job",
    "JobEvent",
    "Limitation",
    "LogEntry",
    "MapFilters",
    "MapStats",
    "Metric",
    "MetricConditions",
    "Paper",
    "PaperExtraction",
    "PaperSections",
    "ResearchMap",
    "Section",
    "Synthesis",
    "Technology",
    "ToolRef",
    "VerificationReport",
]

