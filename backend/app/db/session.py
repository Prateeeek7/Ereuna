"""Database engine.

DATABASE_URL picks the backend:
  - Neon / any Postgres: paste the connection string as Neon shows it
    (postgresql://user:pass@host/db?sslmode=require&channel_binding=require).
  - Unset or sqlite: a local file, for development and tests.
"""

import logging
from pathlib import Path
from typing import Any, Optional
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine
from sqlalchemy.pool import NullPool

from app.config import settings

logger = logging.getLogger(__name__)

_engine: Optional[AsyncEngine] = None

# libpq options asyncpg does not accept as URL parameters.
_LIBPQ_ONLY = {"sslmode", "channel_binding", "options", "target_session_attrs"}


def normalize_database_url(url: str) -> tuple[str, dict[str, Any]]:
    """SQLAlchemy async URL and connect args for a libpq-style or sqlite URL."""
    url = url.strip()
    if not url:
        url = "sqlite+aiosqlite:///data/researchradar.db"
    if url.startswith("sqlite"):
        url = url.replace("sqlite://", "sqlite+aiosqlite://", 1) if "+aiosqlite" not in url else url
        path = url.split(":///", 1)[1] if ":///" in url else ""
        if path and path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        return url, {}

    parts = urlsplit(url)
    scheme = "postgresql+asyncpg"
    query = dict(parse_qsl(parts.query))
    connect_args: dict[str, Any] = {}
    sslmode = query.get("sslmode", "")
    if sslmode in ("require", "verify-ca", "verify-full") or parts.hostname and parts.hostname.endswith(".neon.tech"):
        connect_args["ssl"] = "require"
    if parts.hostname and "-pooler" in parts.hostname:
        # Neon's pooled endpoint is PgBouncer in transaction mode, which cannot
        # hold asyncpg's per-connection prepared statements.
        connect_args["statement_cache_size"] = 0
    query = {k: v for k, v in query.items() if k not in _LIBPQ_ONLY}
    return urlunsplit((scheme, parts.netloc, parts.path, urlencode(query), "")), connect_args


def get_engine() -> AsyncEngine:
    global _engine
    if _engine is None:
        url, connect_args = normalize_database_url(settings.database_url)
        if url.startswith("sqlite"):
            # A connection per operation: cheap for a local file, and safe across
            # the separate event loops tests run on.
            _engine = create_async_engine(url, poolclass=NullPool, connect_args=connect_args)
        else:
            # Neon suspends idle compute and drops connections; check before use.
            _engine = create_async_engine(
                url,
                pool_size=5,
                max_overflow=5,
                pool_pre_ping=True,
                pool_recycle=300,
                connect_args=connect_args,
            )
        logger.info("Database: %s", describe_database())
    return _engine


def describe_database() -> str:
    """Backend and host, never credentials."""
    url, _ = normalize_database_url(settings.database_url)
    if url.startswith("sqlite"):
        return f"SQLite ({url.split(':///', 1)[-1]})"
    return f"Postgres at {urlsplit(url).hostname}"


async def reset_engine() -> None:
    """Dispose the engine so the next use connects to settings.database_url afresh."""
    global _engine
    if _engine is not None:
        await _engine.dispose()
    _engine = None
