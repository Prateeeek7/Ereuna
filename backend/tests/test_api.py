"""Tests for FastAPI maps and jobs endpoints."""

import pytest
from httpx import ASGITransport, AsyncClient

from app.api.state import state_manager
from app.main import app
from app.models.map import MapFilters, MapStats, ResearchMap
from app.models.paper import Author, Paper


@pytest.mark.asyncio
async def test_health_endpoint():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


@pytest.mark.asyncio
async def test_get_map_not_found():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/v1/maps/nonexistent_map")
        assert resp.status_code == 404


@pytest.mark.asyncio
async def test_create_and_get_map():
    # Pre-populate state manager with a map
    map_id = "test_map_001"
    topic = "Low-leakage SRAM using FinFET"
    p1 = Paper(
        id="p1",
        title="Sample FinFET Paper",
        authors=[Author(name="Elena Rostova")],
        year=2020,
        relevance_score=0.92,
        relevance_reason="Top match for SRAM leakage",
    )
    research_map = ResearchMap(
        id=map_id,
        topic=topic,
        normalized_topic=topic.lower(),
        filters=MapFilters(),
        stats=MapStats(total_papers=1),
        papers=[p1],
    )
    await state_manager.store_map(research_map, MapFilters())

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # 1. Fetch map by ID
        resp = await client.get(f"/v1/maps/{map_id}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["id"] == map_id
        assert data["topic"] == topic
        assert len(data["papers"]) == 1
        assert data["papers"][0]["title"] == "Sample FinFET Paper"

        # 2. POST /v1/maps with same topic returns cached status 'ready'
        post_resp = await client.post(
            "/v1/maps",
            json={"topic": topic, "filters": {}},
        )
        assert post_resp.status_code == 202
        post_data = post_resp.json()
        assert post_data["status"] == "ready"
        assert post_data["map_id"] == map_id


@pytest.mark.asyncio
async def test_job_events_stream():
    # Initialize a job with events
    job_id = "test_job_123"
    job = state_manager.create_job(job_id=job_id, map_id="test_map_123")
    job.add_event("stage", {"stage": 1, "name": "expand_query"})
    job.add_event("log", {"message": "Starting search..."})
    job.add_event("done", {"map_id": "test_map_123", "papers_count": 25})

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get(f"/v1/jobs/{job_id}/events")
        assert resp.status_code == 200
        assert "text/event-stream" in resp.headers["content-type"]
        text = resp.text
        assert "event: stage" in text
        assert "event: log" in text
        assert "event: done" in text
