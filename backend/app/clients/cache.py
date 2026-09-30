"""HTTP response cache with 24-hour TTL.

Provides a unified caching layer for external API requests (OpenAlex, Semantic Scholar, arXiv).
Uses Redis if available, falling back gracefully to local SQLite.
"""

import hashlib
import json
import logging
import os
import sqlite3
import time
from pathlib import Path
from typing import Any, Optional

from app.config import settings

logger = logging.getLogger(__name__)

CACHE_TTL_SECONDS = 24 * 60 * 60  # 24 hours


class ResponseCache:
    """Async cache for HTTP client responses with 24h expiration."""

    def __init__(self, db_path: Optional[str] = None) -> None:
        self._redis_client = None
        self._redis_available: Optional[bool] = None

        if db_path:
            self._sqlite_path = Path(db_path)
        else:
            cache_dir = Path.home() / ".cache" / "researchradar"
            cache_dir.mkdir(parents=True, exist_ok=True)
            self._sqlite_path = cache_dir / "http_cache.db"

        self._init_sqlite()

    def _init_sqlite(self) -> None:
        """Initialize the SQLite fallback table."""
        try:
            with sqlite3.connect(self._sqlite_path) as conn:
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS response_cache (
                        key TEXT PRIMARY KEY,
                        data TEXT NOT NULL,
                        expires_at REAL NOT NULL
                    )
                    """
                )
                conn.execute(
                    "CREATE INDEX IF NOT EXISTS idx_expires_at ON response_cache(expires_at)"
                )
        except Exception as e:
            logger.warning("Could not initialize SQLite response cache: %s", e)

    def _make_key(self, source: str, endpoint: str, params: Optional[dict] = None) -> str:
        """Create a deterministic SHA-256 cache key."""
        serialized = json.dumps(params or {}, sort_keys=True)
        raw = f"{source}:{endpoint}:{serialized}"
        digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        return f"radar:cache:{source}:{digest}"

    async def _get_redis(self):
        """Lazy connection to Redis."""
        if self._redis_available is False:
            return None

        if self._redis_client is None:
            try:
                import redis.asyncio as aioredis

                client = aioredis.from_url(settings.redis_url, socket_timeout=1.0)
                await client.ping()
                self._redis_client = client
                self._redis_available = True
                logger.info("Connected to Redis cache at %s", settings.redis_url)
            except Exception as e:
                logger.debug("Redis unavailable (%s), using SQLite cache", e)
                self._redis_available = False
                self._redis_client = None
                return None

        return self._redis_client

    async def get(self, source: str, endpoint: str, params: Optional[dict] = None) -> Optional[Any]:
        """Retrieve cached JSON response if still valid."""
        key = self._make_key(source, endpoint, params)

        # 1. Try Redis
        redis = await self._get_redis()
        if redis:
            try:
                val = await redis.get(key)
                if val is not None:
                    return json.loads(val)
            except Exception as e:
                logger.debug("Redis get error: %s", e)

        # 2. Fallback to SQLite
        try:
            now = time.time()
            with sqlite3.connect(self._sqlite_path) as conn:
                cursor = conn.execute(
                    "SELECT data FROM response_cache WHERE key = ? AND expires_at > ?",
                    (key, now),
                )
                row = cursor.fetchone()
                if row:
                    return json.loads(row[0])
        except Exception as e:
            logger.debug("SQLite cache get error: %s", e)

        return None

    async def set(
        self,
        source: str,
        endpoint: str,
        data: Any,
        params: Optional[dict] = None,
        ttl_seconds: int = CACHE_TTL_SECONDS,
    ) -> None:
        """Store response data with expiration."""
        key = self._make_key(source, endpoint, params)
        payload = json.dumps(data)

        # 1. Try Redis
        redis = await self._get_redis()
        if redis:
            try:
                await redis.setex(key, ttl_seconds, payload)
                return
            except Exception as e:
                logger.debug("Redis set error: %s", e)

        # 2. Fallback to SQLite
        try:
            expires_at = time.time() + ttl_seconds
            with sqlite3.connect(self._sqlite_path) as conn:
                conn.execute(
                    """
                    INSERT INTO response_cache (key, data, expires_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(key) DO UPDATE SET data=excluded.data, expires_at=excluded.expires_at
                    """,
                    (key, payload, expires_at),
                )
        except Exception as e:
            logger.debug("SQLite cache set error: %s", e)

    async def clear_expired(self) -> None:
        """Purge expired entries from SQLite."""
        try:
            now = time.time()
            with sqlite3.connect(self._sqlite_path) as conn:
                conn.execute("DELETE FROM response_cache WHERE expires_at <= ?", (now,))
        except Exception as e:
            logger.debug("Failed to purge expired SQLite cache: %s", e)

    async def close(self) -> None:
        """Close cache connections."""
        if self._redis_client:
            try:
                await self._redis_client.aclose()
            except Exception:
                pass


# Global singleton
cache = ResponseCache()
