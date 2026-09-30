"""Job tracking models."""

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class JobStatus(str, Enum):
    """Job processing status."""

    QUEUED = "queued"
    PROCESSING = "processing"
    DONE = "done"
    ERROR = "error"


class LogEntry(BaseModel):
    """A single timestamped log line."""

    timestamp: datetime = Field(default_factory=datetime.now)
    message: str


class Job(BaseModel):
    """A pipeline processing job."""

    id: str
    map_id: str
    status: JobStatus = JobStatus.QUEUED
    stage: str | None = None
    progress: float = 0.0
    log: list[LogEntry] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.now)


class JobEvent(BaseModel):
    """An SSE event emitted during job processing."""

    event: str  # stage, log, paper_selected, done, error
    data: dict
