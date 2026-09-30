"""OpenAlex API client with caching, retries, and rate limiting.

Provides paper metadata, references, and concepts.
Uses the polite pool (email-based) for higher rate limits.
Docs: https://docs.openalex.org/
"""

import asyncio
import logging
import time
from typing import Any, Optional

import httpx
from tenacity import retry, retry_if_exception, stop_after_attempt, wait_exponential

from app.clients.cache import cache
from app.config import settings

logger = logging.getLogger(__name__)


def reconstruct_abstract(inverted_index: Optional[dict[str, list[int]]]) -> str:
    """Reconstruct plain text abstract from OpenAlex abstract_inverted_index."""
    if not inverted_index:
        return ""
    try:
        valid_positions = [
            pos for positions in inverted_index.values() for pos in positions
            if isinstance(pos, int) and 0 <= pos < 5000
        ]
        if not valid_positions:
            return ""
        max_pos = max(valid_positions)
        words = [""] * (max_pos + 1)
        for word, positions in inverted_index.items():
            for pos in positions:
                if 0 <= pos <= max_pos:
                    words[pos] = word
        return " ".join(w for w in words if w)
    except Exception as e:
        logger.debug("Failed to reconstruct inverted index abstract: %s", e)
        return ""


def _is_retryable(exc: BaseException) -> bool:
    """Retry transient failures, but not a 429 whose budget resets hours from now."""
    if isinstance(exc, httpx.HTTPStatusError):
        status = exc.response.status_code
        if status == 429:
            try:
                return float(exc.response.headers.get("retry-after", "0")) <= 30
            except ValueError:
                return False
        return status >= 500
    return isinstance(exc, (httpx.TransportError, httpx.TimeoutException))


class OpenAlexClient:
    """Client for the OpenAlex API."""

    BASE_URL = "https://api.openalex.org"

    def __init__(self, client: Optional[httpx.AsyncClient] = None) -> None:
        self._email = settings.openalex_email
        user_agent = f"Ereuna/0.1 (mailto:{self._email})" if self._email else "Ereuna/0.1"
        headers = {"User-Agent": user_agent, "Accept": "application/json"}
        if settings.openalex_api_key:
            headers["Authorization"] = f"Bearer {settings.openalex_api_key}"
        self._client = client or httpx.AsyncClient(
            base_url=self.BASE_URL,
            timeout=30.0,
            headers=headers,
        )
        self._lock = asyncio.Lock()
        self._last_request_time = 0.0

    async def _throttle(self) -> None:
        """Polite pool throttling: min 0.3s between calls."""
        async with self._lock:
            now = time.time()
            elapsed = now - self._last_request_time
            if elapsed < 0.3:
                await asyncio.sleep(0.3 - elapsed)
            self._last_request_time = time.time()

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1.5, min=2, max=10),
        retry=retry_if_exception(_is_retryable),
        reraise=True,
    )
    async def _fetch(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        """Execute HTTP request with caching, throttling, and retry."""
        cached = await cache.get("openalex", endpoint, params)
        if cached is not None:
            return cached

        await self._throttle()
        resp = await self._client.get(endpoint, params=params)
        resp.raise_for_status()
        data = resp.json()
        await cache.set("openalex", endpoint, data, params=params)
        return data


    async def search_works(
        self,
        query: str,
        per_page: int = 50,
        filters: Optional[dict[str, Any]] = None,
    ) -> list[dict[str, Any]]:
        """Search for works matching a query string.

        Args:
            query: Search query string.
            per_page: Number of results (max 200).
            filters: Optional OpenAlex filter expressions.

        Returns:
            List of work objects from OpenAlex.
        """
        params: dict[str, Any] = {
            "search": query,
            "per-page": min(per_page, 200),
            "sort": "relevance_score:desc",
        }
        if filters:
            filter_str = ",".join(f"{k}:{v}" for k, v in filters.items())
            params["filter"] = filter_str

        try:
            data = await self._fetch("/works", params)
            return data.get("results", [])
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 429:
                logger.warning(
                    "OpenAlex rate limit reached (set OPENALEX_API_KEY for a dedicated budget): %s",
                    e.response.text[:160],
                )
            else:
                logger.warning("OpenAlex search failed for '%s': %s", query, e)
            return []
        except Exception as e:
            logger.warning("OpenAlex search failed for '%s': %s", query, e)
            return []

    async def filter_works(self, filter_expr: str, select: str, per_page: int = 50) -> list[dict[str, Any]]:
        """List works matching an OpenAlex filter expression (e.g. 'doi:a|b'), selecting fields.

        Raises on HTTP errors so callers can distinguish "no match" from "lookup failed".
        """
        data = await self._fetch(
            "/works", {"filter": filter_expr, "select": select, "per-page": min(per_page, 200)}
        )
        return data.get("results", [])

    async def get_work(self, openalex_id: str) -> Optional[dict[str, Any]]:
        """Get a single work by OpenAlex ID or DOI."""
        clean_id = openalex_id.strip()
        if not clean_id.startswith("http"):
            clean_id = f"https://openalex.org/{clean_id}"

        try:
            return await self._fetch(f"/works/{clean_id}", {})
        except Exception as e:
            logger.warning("OpenAlex get_work failed for '%s': %s", openalex_id, e)
            return None

    async def close(self) -> None:
        """Close the underlying HTTP client."""
        await self._client.aclose()
