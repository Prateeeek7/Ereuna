"""Jobs API routes — Server-Sent Events (SSE) streaming for pipeline progress."""

import asyncio
import logging
from typing import AsyncGenerator

from fastapi import APIRouter, HTTPException, Request
from sse_starlette.sse import EventSourceResponse

from app.api.state import state_manager

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/jobs/{job_id}/events")
async def job_events(job_id: str, request: Request):
    """Stream Server-Sent Events (SSE) for research map generation progress.

    Event types emitted:
      - stage: Pipeline stage progress (1=expand_query, 2=retrieve, 3=rank)
      - log: Plain-text timestamped progress messages
      - paper_selected: Key metadata of high-ranking papers as they are selected
      - done: Pipeline completed with final map ID and paper count
      - error: Fatal or recoverable pipeline error
    """
    job = state_manager.get_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail=f"Job '{job_id}' not found")

    async def event_generator() -> AsyncGenerator[dict[str, str], None]:
        # Stream from the job's event history by index, so a client that connects
        # late (or reconnects) gets every event exactly once, in order, and any
        # number of clients can follow the same job.
        sent = 0
        while True:
            if await request.is_disconnected():
                logger.debug("Client disconnected from SSE stream for job %s", job_id)
                return
            if sent < len(job.events):
                ev = job.events[sent]
                sent += 1
                yield {"event": ev["event"], "data": ev["data"]}
                if ev["event"] in ("done", "error"):
                    return
                continue
            if job.is_done:
                return
            await asyncio.sleep(0.25)

    return EventSourceResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
