"""Arq worker configuration and task definitions."""

from arq.connections import RedisSettings

from app.api.maps import _run_pipeline_task
from app.api.state import state_manager
from app.config import settings
from app.models.map import MapFilters


async def run_pipeline(ctx: dict, map_id: str, topic: str, filters: dict) -> dict:
    """Run the full research map pipeline via Arq worker.

    Stages:
    1. expand_query
    2. retrieve
    3. rank
    4. fetch_fulltext
    5. extract
    6. analyze
    7. graph
    8. experiments

    Emits SSE events at each stage.
    """
    map_filters = MapFilters(**filters)
    job_id = f"job-arq-{map_id}"
    job = state_manager.create_job(job_id=job_id, map_id=map_id)
    await _run_pipeline_task(job_id=job_id, topic=topic, filters=map_filters, map_id=map_id, job=job)
    return {"map_id": map_id, "job_id": job_id, "status": "done"}


class WorkerSettings:
    """Arq worker settings."""

    functions = [run_pipeline]
    redis_settings = RedisSettings.from_dsn(settings.redis_url)
    max_jobs = 5
    job_timeout = 600  # 10 minutes max per job
