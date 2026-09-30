"""Shared state for maps, libraries and background jobs.

Maps, libraries and quotas live in the database (app.db.store). Job event
streams stay in memory: they only matter while a map is being built.
"""

import datetime
import hashlib
import json
import logging
from typing import Any, Optional

from app.db import store
from app.models.map import MapFilters, ResearchMap
from app.models.paper import Paper

logger = logging.getLogger(__name__)


def make_map_cache_key(topic: str, filters: MapFilters) -> str:
    """Generate deterministic hash key for topic + filters."""
    norm_topic = " ".join(topic.strip().lower().split())
    filter_data = filters.model_dump()
    serialized = json.dumps(filter_data, sort_keys=True)
    raw = f"{norm_topic}:{serialized}"
    return hashlib.sha256(raw.encode()).hexdigest()[:16]


class JobEventStream:
    """Event queue for a single background job."""

    def __init__(self, job_id: str, map_id: str) -> None:
        self.job_id = job_id
        self.map_id = map_id
        self.events: list[dict[str, Any]] = []
        self.is_done = False
        self.created_at = datetime.datetime.now()

    def add_event(self, event_type: str, data: dict[str, Any]) -> None:
        """Add event to history and notify active subscribers."""
        event = {"event": event_type, "data": json.dumps(data)}
        self.events.append(event)
        if event_type == "done" or event_type == "error":
            self.is_done = True


class PipelineStateManager:
    """Maps, libraries and quotas (persisted) plus live job event streams."""

    def __init__(self) -> None:
        self._jobs: dict[str, JobEventStream] = {}

    async def get_cached_map_id(self, topic: str, filters: MapFilters) -> Optional[str]:
        """Id of a completed map for the same topic and filters, if any."""
        return await store.find_map_id(make_map_cache_key(topic, filters))

    async def store_map(self, research_map: ResearchMap, filters: Optional[MapFilters] = None) -> None:
        """Persist a generated research map.

        Parsed full text (paper.sections) is only needed while extracting and is
        about 90% of a map's size, so it is dropped: every extracted item already
        carries its verified quote, page and section.
        """
        f = filters if filters is not None else research_map.filters
        # Maps built from someone's PDFs are private: never served from the topic cache.
        cache_key = f"upload_{research_map.id}" if research_map.source == "upload" else make_map_cache_key(research_map.topic, f)
        lean = research_map.model_copy(update={
            "papers": [p.model_copy(update={"sections": None}) for p in research_map.papers],
        })
        await store.put_map(lean, cache_key)

    async def get_map(self, map_id: str, user_id: Optional[str] = None) -> Optional[ResearchMap]:
        """The map, or None when it doesn't exist or is someone else's private map."""
        research_map = await store.get_map(map_id)
        if research_map is not None and research_map.owner_id and research_map.owner_id != user_id:
            return None
        return research_map

    async def get_paper(self, paper_id: str, user_id: Optional[str] = None) -> Optional[Paper]:
        """A paper as it appears in the latest map that contains it."""
        map_id = await store.map_id_for_paper(paper_id)
        research_map = await self.get_map(map_id, user_id) if map_id else None
        if research_map is None:
            return None
        return next((p for p in research_map.papers if p.id == paper_id), None)

    async def save_map(self, user_id: str, map_id: str) -> bool:
        if await self.get_map(map_id, user_id) is None:
            return False
        await store.add_saved(store.saved_maps, user_id, "map_id", map_id)
        return True

    async def remove_saved_map(self, user_id: str, map_id: str) -> None:
        await store.remove_saved(store.saved_maps, user_id, "map_id", map_id)

    async def get_saved_maps(self, user_id: str) -> list[ResearchMap]:
        result = []
        for map_id in await store.list_saved(store.saved_maps, user_id, "map_id"):
            research_map = await self.get_map(map_id, user_id)
            if research_map is not None:
                result.append(research_map)
        return result

    async def save_paper(self, user_id: str, paper_id: str) -> bool:
        if await self.get_paper(paper_id, user_id) is None:
            return False
        await store.add_saved(store.saved_papers, user_id, "paper_id", paper_id)
        return True

    async def remove_saved_paper(self, user_id: str, paper_id: str) -> None:
        await store.remove_saved(store.saved_papers, user_id, "paper_id", paper_id)

    async def get_saved_papers(self, user_id: str) -> list[Paper]:
        result = []
        for paper_id in await store.list_saved(store.saved_papers, user_id, "paper_id"):
            paper = await self.get_paper(paper_id, user_id)
            if paper is not None:
                result.append(paper)
        return result

    async def record_map_created(self, user_id: str) -> int:
        """Count a new map for today (UTC); returns today's count including this one."""
        return await store.increment_quota(user_id)

    async def maps_created_today(self, user_id: str) -> int:
        return await store.quota_used(user_id)

    def create_job(self, job_id: str, map_id: str) -> JobEventStream:
        """Initialize a new job stream."""
        job = JobEventStream(job_id=job_id, map_id=map_id)
        self._jobs[job_id] = job
        return job

    def get_job(self, job_id: str) -> Optional[JobEventStream]:
        """Get an existing job stream."""
        return self._jobs.get(job_id)


# Global singleton
state_manager = PipelineStateManager()
