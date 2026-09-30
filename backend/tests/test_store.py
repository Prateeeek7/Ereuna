"""Persistence: maps, papers, libraries, quotas and accounts outlive the process."""

import sqlite3

import pytest

from app.api.state import make_map_cache_key, state_manager
from app.config import settings
from app.db import session, store
from app.db.session import normalize_database_url
from app.models.map import MapFilters, ResearchMap
from app.models.paper import Paper


def _map(map_id: str = "map_persist", topic: str = "Solid-state batteries") -> ResearchMap:
    papers = [Paper(id=f"{map_id}_p{i}", title=f"Paper {i}", year=2020 + i) for i in range(3)]
    return ResearchMap(id=map_id, topic=topic, normalized_topic=topic.lower(), filters=MapFilters(), papers=papers)


def _restart() -> None:
    """Drop everything held in memory, as a server restart would."""
    session._engine = None
    store.reset_schema_state()


@pytest.mark.asyncio
async def test_maps_and_papers_survive_a_restart():
    await state_manager.store_map(_map())
    _restart()

    loaded = await state_manager.get_map("map_persist")
    assert loaded is not None and loaded.topic == "Solid-state batteries"
    assert [p.id for p in loaded.papers] == ["map_persist_p0", "map_persist_p1", "map_persist_p2"]
    paper = await state_manager.get_paper("map_persist_p1")
    assert paper is not None and paper.title == "Paper 1"
    assert await state_manager.get_cached_map_id("  solid-state   BATTERIES ", MapFilters()) == "map_persist"
    assert await state_manager.get_map("map_missing") is None


@pytest.mark.asyncio
async def test_rerunning_a_map_replaces_it_in_place():
    await state_manager.store_map(_map())
    updated = _map()
    updated.papers = updated.papers[:1]
    await state_manager.store_map(updated)
    _restart()
    assert len((await state_manager.get_map("map_persist")).papers) == 1


@pytest.mark.asyncio
async def test_maps_are_stored_compressed():
    await state_manager.store_map(_map())
    path = settings.database_url.split(":///", 1)[1]
    with sqlite3.connect(path) as conn:
        blob = conn.execute("SELECT data FROM maps WHERE id = 'map_persist'").fetchone()[0]
    assert blob[:2] == b"\x1f\x8b"  # gzip magic


@pytest.mark.asyncio
async def test_libraries_persist_newest_first_and_per_user():
    await state_manager.store_map(_map("map_a", "Topic A"))
    await state_manager.store_map(_map("map_b", "Topic B"))
    assert await state_manager.save_map("usr_1", "map_a")
    assert await state_manager.save_map("usr_1", "map_b")
    assert await state_manager.save_map("usr_1", "map_a")  # saving twice is harmless
    assert not await state_manager.save_map("usr_1", "map_unknown")
    assert await state_manager.save_paper("usr_1", "map_a_p0")
    _restart()

    assert [m.id for m in await state_manager.get_saved_maps("usr_1")] == ["map_a", "map_b"]
    assert [p.id for p in await state_manager.get_saved_papers("usr_1")] == ["map_a_p0"]
    assert await state_manager.get_saved_maps("usr_2") == []

    await state_manager.remove_saved_map("usr_1", "map_a")
    assert [m.id for m in await state_manager.get_saved_maps("usr_1")] == ["map_b"]


@pytest.mark.asyncio
async def test_daily_quota_counts_and_persists():
    assert await state_manager.maps_created_today("usr_q") == 0
    assert await state_manager.record_map_created("usr_q") == 1
    assert await state_manager.record_map_created("usr_q") == 2
    _restart()
    assert await state_manager.maps_created_today("usr_q") == 2
    assert await state_manager.maps_created_today("usr_other") == 0


@pytest.mark.asyncio
async def test_legacy_local_accounts_are_imported_once(tmp_path, monkeypatch):
    legacy = tmp_path / "legacy" / "users.db"
    legacy.parent.mkdir()
    with sqlite3.connect(legacy) as conn:
        conn.execute("CREATE TABLE users (id TEXT, email TEXT, name TEXT, password_hash TEXT, salt TEXT, created_at REAL)")
        conn.execute("INSERT INTO users VALUES ('usr_old', 'old@example.org', 'Old', ?, ?, 1.0)", ("a" * 64, "b" * 32))
    (legacy.parent / "auth_secret.key").write_text("legacy-key\n")
    monkeypatch.setattr(settings, "auth_db_path", str(legacy))
    _restart()

    row = await store.find_user_by_id("usr_old")
    assert row is not None and row["email"] == "old@example.org"
    assert await store.get_or_create_setting("auth_secret", lambda: "new") == "legacy-key"


def test_neon_connection_string_is_used_as_pasted():
    url, args = normalize_database_url(
        "postgresql://alex:pw@ep-cool-lab-123-pooler.eu-central-1.aws.neon.tech/neondb"
        "?sslmode=require&channel_binding=require"
    )
    assert url == "postgresql+asyncpg://alex:pw@ep-cool-lab-123-pooler.eu-central-1.aws.neon.tech/neondb"
    assert args == {"ssl": "require", "statement_cache_size": 0}

    direct, direct_args = normalize_database_url("postgres://u:p@ep-x.us-east-2.aws.neon.tech/db?sslmode=require")
    assert direct.startswith("postgresql+asyncpg://") and direct_args == {"ssl": "require"}


def test_sqlite_is_the_default_for_local_development():
    url, args = normalize_database_url("")
    assert url == "sqlite+aiosqlite:///data/researchradar.db" and args == {}


def test_database_description_never_includes_the_password(monkeypatch):
    monkeypatch.setattr(settings, "database_url", "postgresql://u:secret-pw@ep-x.aws.neon.tech/db?sslmode=require")
    text = session.describe_database()
    assert "secret-pw" not in text and "ep-x.aws.neon.tech" in text


def test_cache_key_ignores_case_and_spacing():
    assert make_map_cache_key("A  Topic", MapFilters()) == make_map_cache_key(" a topic ", MapFilters())


@pytest.mark.asyncio
async def test_parsed_full_text_is_not_stored():
    from app.models.paper import PaperSections, Section

    research_map = _map()
    research_map.papers[0].has_full_text = True
    research_map.papers[0].sections = PaperSections(
        sections=[Section(heading="Results", text="A long parsed body " * 500)], full_text="A long parsed body " * 500,
    )
    await state_manager.store_map(research_map)
    _restart()
    paper = await state_manager.get_paper("map_persist_p0")
    assert paper.has_full_text and paper.sections is None
