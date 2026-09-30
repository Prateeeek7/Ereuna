"""Persistent storage: accounts, research maps, libraries and daily quotas.

Maps are stored whole as gzip-compressed JSON (about 135 KB for a 25-paper map)
and looked up by id or by topic+filters cache key. Papers are served from the
map that contains them via a small paper -> map index, so nothing is stored twice.
"""

import asyncio
import datetime
import gzip
import logging
import sqlite3
import time
from collections import OrderedDict
from pathlib import Path
from typing import Any, Optional

from sqlalchemy import (
    Column,
    Float,
    Index,
    Integer,
    LargeBinary,
    MetaData,
    String,
    Table,
    Text,
    delete,
    func,
    select,
)
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.exc import IntegrityError

from app.config import settings
from app.db.session import get_engine
from app.models.map import ResearchMap

logger = logging.getLogger(__name__)

metadata = MetaData()

users = Table(
    "users",
    metadata,
    Column("id", String(40), primary_key=True),
    Column("email", String(320), nullable=False, unique=True),
    Column("name", String(200), nullable=False),
    Column("password_hash", String(128), nullable=False),
    Column("salt", String(64), nullable=False),
    Column("created_at", Float, nullable=False),
)

maps = Table(
    "maps",
    metadata,
    Column("id", String(40), primary_key=True),
    Column("cache_key", String(32), nullable=False),
    Column("topic", Text, nullable=False),
    Column("created_at", Float, nullable=False),
    Column("updated_at", Float, nullable=False),
    Column("data", LargeBinary, nullable=False),  # gzip(ResearchMap JSON)
    Index("ix_maps_cache_key", "cache_key"),
)

paper_index = Table(
    "paper_index",
    metadata,
    Column("paper_id", String(200), primary_key=True),
    Column("map_id", String(40), nullable=False),
)

saved_maps = Table(
    "saved_maps",
    metadata,
    Column("user_id", String(40), primary_key=True),
    Column("map_id", String(40), primary_key=True),
    Column("saved_at", Float, nullable=False),
)

saved_papers = Table(
    "saved_papers",
    metadata,
    Column("user_id", String(40), primary_key=True),
    Column("paper_id", String(200), primary_key=True),
    Column("saved_at", Float, nullable=False),
)

map_quota = Table(
    "map_quota",
    metadata,
    Column("user_id", String(40), primary_key=True),
    Column("day", String(10), primary_key=True),
    Column("count", Integer, nullable=False),
)

app_settings = Table(
    "app_settings",
    metadata,
    Column("key", String(64), primary_key=True),
    Column("value", Text, nullable=False),
)


# ──────────────────────────────────────────────────────────
# Schema
# ──────────────────────────────────────────────────────────

_ready_loops: set[int] = set()
_init_locks: dict[int, asyncio.Lock] = {}


async def ensure_schema() -> None:
    """Create tables on first use (idempotent) and import legacy local accounts."""
    loop_id = id(asyncio.get_running_loop())
    if loop_id in _ready_loops:
        return
    lock = _init_locks.setdefault(loop_id, asyncio.Lock())
    async with lock:
        if loop_id in _ready_loops:
            return
        async with get_engine().begin() as conn:
            await conn.run_sync(metadata.create_all)
        await _import_legacy_accounts()
        _ready_loops.add(loop_id)


def reset_schema_state() -> None:
    """Forget that the schema was checked (after switching databases)."""
    _ready_loops.clear()
    _init_locks.clear()
    _map_cache.clear()
    _settings_cache.clear()


async def _import_legacy_accounts() -> None:
    """One-time copy of accounts and the token key from the old data/users.db file,
    so existing sign-ins keep working after moving to this database."""
    legacy = Path(settings.auth_db_path)
    if not legacy.exists():
        return
    async with get_engine().begin() as conn:
        if await conn.scalar(select(func.count()).select_from(users)):
            return
        try:
            with sqlite3.connect(legacy) as old:
                rows = old.execute(
                    "SELECT id, email, name, password_hash, salt, created_at FROM users"
                ).fetchall()
        except sqlite3.Error:
            return
        for r in rows:
            await conn.execute(users.insert().values(
                id=r[0], email=r[1], name=r[2], password_hash=r[3], salt=r[4], created_at=r[5],
            ))
        key_file = legacy.with_name("auth_secret.key")
        existing_key = await conn.scalar(select(app_settings.c.value).where(app_settings.c.key == "auth_secret"))
        if key_file.exists() and existing_key is None:
            await conn.execute(app_settings.insert().values(key="auth_secret", value=key_file.read_text().strip()))
    if rows:
        logger.info("Imported %d account(s) from %s", len(rows), legacy)


def _upsert(conn: Any, table: Table, values: dict[str, Any], keys: list[str]) -> Any:
    insert = pg_insert if conn.dialect.name == "postgresql" else sqlite_insert
    stmt = insert(table).values(**values)
    updates = {k: stmt.excluded[k] for k in values if k not in keys}
    return stmt.on_conflict_do_update(index_elements=keys, set_=updates) if updates else stmt.on_conflict_do_nothing(index_elements=keys)


# ──────────────────────────────────────────────────────────
# Settings (token signing key)
# ──────────────────────────────────────────────────────────


_settings_cache: dict[str, str] = {}


async def get_or_create_setting(key: str, make_value: Any) -> str:
    """Stored value for key, creating it with make_value() the first time (cached)."""
    if key in _settings_cache:
        return _settings_cache[key]
    await ensure_schema()
    async with get_engine().begin() as conn:
        value = await conn.scalar(select(app_settings.c.value).where(app_settings.c.key == key))
        if value is not None:
            _settings_cache[key] = value
            return value
        insert = pg_insert if conn.dialect.name == "postgresql" else sqlite_insert
        await conn.execute(
            insert(app_settings).values(key=key, value=make_value()).on_conflict_do_nothing(index_elements=["key"])
        )
        # Another process may have won the race; always return the stored value.
        value = await conn.scalar(select(app_settings.c.value).where(app_settings.c.key == key))
    _settings_cache[key] = value
    return value


# ──────────────────────────────────────────────────────────
# Accounts
# ──────────────────────────────────────────────────────────


async def insert_user(row: dict[str, Any]) -> bool:
    """Insert a user; False when the email is already registered."""
    await ensure_schema()
    try:
        async with get_engine().begin() as conn:
            await conn.execute(users.insert().values(**row))
        return True
    except IntegrityError:
        return False


async def find_user_by_email(email: str) -> Optional[dict[str, Any]]:
    await ensure_schema()
    async with get_engine().connect() as conn:
        row = (await conn.execute(select(users).where(users.c.email == email))).mappings().first()
    return dict(row) if row else None


async def find_user_by_id(user_id: str) -> Optional[dict[str, Any]]:
    await ensure_schema()
    async with get_engine().connect() as conn:
        row = (await conn.execute(select(users).where(users.c.id == user_id))).mappings().first()
    return dict(row) if row else None


async def delete_user(user_id: str) -> None:
    """Remove an account and everything that belongs only to it: library entries,
    quota rows and the private maps built from its uploaded PDFs. Topic maps are
    shared by everyone who searched the same topic, so they stay."""
    await ensure_schema()
    async with get_engine().begin() as conn:
        upload_rows = (await conn.execute(
            select(maps.c.id, maps.c.data).where(maps.c.cache_key.like("upload\\_%", escape="\\"))
        )).all()
        owned = [
            map_id for map_id, blob in upload_rows
            if (await asyncio.to_thread(_decode, blob)).owner_id == user_id
        ]
        if owned:
            await conn.execute(delete(paper_index).where(paper_index.c.map_id.in_(owned)))
            await conn.execute(delete(maps).where(maps.c.id.in_(owned)))
        for table in (saved_maps, saved_papers, map_quota):
            await conn.execute(delete(table).where(table.c.user_id == user_id))
        await conn.execute(delete(users).where(users.c.id == user_id))
    for map_id in owned:
        _map_cache.pop(map_id, None)


# ──────────────────────────────────────────────────────────
# Maps
# ──────────────────────────────────────────────────────────

# Recently used maps, parsed, so repeat reads (map, graph, papers, exports) skip
# the database round trip and decompression.
_MAP_CACHE_SIZE = 24
_map_cache: "OrderedDict[str, ResearchMap]" = OrderedDict()


def _remember(research_map: ResearchMap) -> None:
    _map_cache[research_map.id] = research_map
    _map_cache.move_to_end(research_map.id)
    while len(_map_cache) > _MAP_CACHE_SIZE:
        _map_cache.popitem(last=False)


def _encode(research_map: ResearchMap) -> bytes:
    return gzip.compress(research_map.model_dump_json().encode(), compresslevel=6)


def _decode(blob: bytes) -> ResearchMap:
    return ResearchMap.model_validate_json(gzip.decompress(blob))


async def put_map(research_map: ResearchMap, cache_key: str) -> None:
    await ensure_schema()
    now = time.time()
    blob = await asyncio.to_thread(_encode, research_map)
    async with get_engine().begin() as conn:
        created = await conn.scalar(select(maps.c.created_at).where(maps.c.id == research_map.id))
        await conn.execute(_upsert(conn, maps, {
            "id": research_map.id,
            "cache_key": cache_key,
            "topic": research_map.topic,
            "created_at": created or now,
            "updated_at": now,
            "data": blob,
        }, ["id"]))
        for paper in research_map.papers:
            await conn.execute(_upsert(conn, paper_index, {"paper_id": paper.id, "map_id": research_map.id}, ["paper_id"]))
    _remember(research_map)


async def get_map(map_id: str) -> Optional[ResearchMap]:
    if map_id in _map_cache:
        _map_cache.move_to_end(map_id)
        return _map_cache[map_id]
    await ensure_schema()
    async with get_engine().connect() as conn:
        blob = await conn.scalar(select(maps.c.data).where(maps.c.id == map_id))
    if blob is None:
        return None
    research_map = await asyncio.to_thread(_decode, blob)
    _remember(research_map)
    return research_map


async def find_map_id(cache_key: str) -> Optional[str]:
    await ensure_schema()
    async with get_engine().connect() as conn:
        return await conn.scalar(
            select(maps.c.id).where(maps.c.cache_key == cache_key).order_by(maps.c.updated_at.desc()).limit(1)
        )


async def map_id_for_paper(paper_id: str) -> Optional[str]:
    await ensure_schema()
    async with get_engine().connect() as conn:
        return await conn.scalar(select(paper_index.c.map_id).where(paper_index.c.paper_id == paper_id))


# ──────────────────────────────────────────────────────────
# Libraries
# ──────────────────────────────────────────────────────────


async def add_saved(table: Table, user_id: str, item_col: str, item_id: str) -> None:
    await ensure_schema()
    async with get_engine().begin() as conn:
        await conn.execute(_upsert(conn, table, {"user_id": user_id, item_col: item_id, "saved_at": time.time()}, ["user_id", item_col]))


async def remove_saved(table: Table, user_id: str, item_col: str, item_id: str) -> None:
    await ensure_schema()
    async with get_engine().begin() as conn:
        await conn.execute(delete(table).where(table.c.user_id == user_id, table.c[item_col] == item_id))


async def list_saved(table: Table, user_id: str, item_col: str) -> list[str]:
    """Saved ids for a user, most recently saved first."""
    await ensure_schema()
    async with get_engine().connect() as conn:
        rows = await conn.execute(
            select(table.c[item_col]).where(table.c.user_id == user_id).order_by(table.c.saved_at.desc())
        )
        return [r[0] for r in rows]


# ──────────────────────────────────────────────────────────
# Daily quota
# ──────────────────────────────────────────────────────────


def _today() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")


async def increment_quota(user_id: str) -> int:
    await ensure_schema()
    day = _today()
    async with get_engine().begin() as conn:
        insert = pg_insert if conn.dialect.name == "postgresql" else sqlite_insert
        stmt = insert(map_quota).values(user_id=user_id, day=day, count=1)
        stmt = stmt.on_conflict_do_update(
            index_elements=["user_id", "day"], set_={"count": map_quota.c.count + 1}
        )
        await conn.execute(stmt)
        return await conn.scalar(
            select(map_quota.c.count).where(map_quota.c.user_id == user_id, map_quota.c.day == day)
        ) or 0


async def quota_used(user_id: str) -> int:
    await ensure_schema()
    async with get_engine().connect() as conn:
        return await conn.scalar(
            select(map_quota.c.count).where(map_quota.c.user_id == user_id, map_quota.c.day == _today())
        ) or 0
