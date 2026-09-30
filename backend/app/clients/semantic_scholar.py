"""Semantic Scholar Graph API client with caching, retries, and rate limiting.

Provides citations, abstracts, and open-access PDF links.
Docs: https://api.semanticscholar.org/
"""

import asyncio
import logging
import time
from typing import Any, Optional

import httpx

from app.clients.cache import cache
from app.config import settings

logger = logging.getLogger(__name__)

FIELDS = "paperId,externalIds,title,abstract,venue,year,citationCount,openAccessPdf,authors"


class SemanticScholarClient:
    """Client for the Semantic Scholar Graph API."""

    BASE_URL = "https://api.semanticscholar.org/graph/v1"

    def __init__(self, client: Optional[httpx.AsyncClient] = None) -> None:
        headers = {
            "Accept": "application/json",
            "User-Agent": "Ereuna/0.1",
        }
        if settings.semantic_scholar_api_key:
            headers["x-api-key"] = settings.semantic_scholar_api_key

        self._client = client or httpx.AsyncClient(
            base_url=self.BASE_URL,
            timeout=10.0,
            headers=headers,
        )
        # Minimum gap between requests, from SEMANTIC_SCHOLAR_RPS.
        self._min_interval = 1.0 / max(0.1, settings.semantic_scholar_rps)

    # Shared across all instances: the pipeline creates several clients (search,
    # abstract backfill, citation graph) and the rate limit applies per key.
    _last_request_time: float = 0.0
    _locks: dict[int, asyncio.Lock] = {}

    _MAX_ATTEMPTS = 3
    _RETRYABLE_STATUS = {429, 500, 502, 503, 504}

    async def _request(self, method: str, endpoint: str, **kwargs: Any) -> httpx.Response:
        """Send one request under the process-wide rate limit.

        The key's limit is cumulative across endpoints, so requests are fully
        serialised: the lock is held for the whole round trip and the gap is
        measured from when the previous response arrived. Spacing request starts
        alone lets slow and fast responses land at S2 closer than the limit.
        429s and 5xx back off (honouring Retry-After) before retrying.
        """
        loop_id = id(asyncio.get_running_loop())
        lock = SemanticScholarClient._locks.setdefault(loop_id, asyncio.Lock())
        async with lock:
            for attempt in range(1, self._MAX_ATTEMPTS + 1):
                elapsed = time.time() - SemanticScholarClient._last_request_time
                if elapsed < self._min_interval:
                    await asyncio.sleep(self._min_interval - elapsed)
                try:
                    resp = await self._client.request(method, endpoint, **kwargs)
                except (httpx.TransportError, httpx.TimeoutException):
                    if attempt == self._MAX_ATTEMPTS:
                        raise
                    await asyncio.sleep(attempt)
                    continue
                finally:
                    SemanticScholarClient._last_request_time = time.time()
                if resp.status_code in self._RETRYABLE_STATUS and attempt < self._MAX_ATTEMPTS:
                    await asyncio.sleep(self._backoff(resp, attempt))
                    continue
                resp.raise_for_status()
                return resp
        raise RuntimeError("unreachable")

    @staticmethod
    def _backoff(resp: httpx.Response, attempt: int) -> float:
        retry_after = resp.headers.get("retry-after", "")
        if retry_after.isdigit():
            return min(float(retry_after), 10.0)
        return 2.0 * attempt

    async def _fetch(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        """GET with caching, rate limiting, and retry."""
        cached = await cache.get("semantic_scholar", endpoint, params)
        if cached is not None:
            return cached

        resp = await self._request("GET", endpoint, params=params)
        data = resp.json()
        await cache.set("semantic_scholar", endpoint, data, params=params)
        return data

    async def search_papers(self, query: str, limit: int = 50) -> list[dict[str, Any]]:
        """Search for papers matching a query.

        Args:
            query: Topic or keyword search query.
            limit: Maximum papers to return (up to 100).

        Returns:
            List of paper objects.
        """
        params = {
            "query": query,
            "limit": min(limit, 100),
            "fields": FIELDS,
        }
        try:
            data = await self._fetch("/paper/search", params)
            return data.get("data", [])
        except Exception as e:
            logger.warning("Semantic Scholar search failed for '%s': %s", query, e)
            return []

    async def match_title(self, title: str) -> Optional[dict[str, Any]]:
        """The paper whose title best matches, or None (S2's title-match endpoint)."""
        try:
            data = await self._fetch("/paper/search/match", {"query": title, "fields": FIELDS})
        except Exception as e:
            logger.debug("Semantic Scholar title match failed for '%s': %s", title, e)
            return None
        matches = data.get("data") or []
        return matches[0] if matches else None

    async def get_paper(self, paper_id: str) -> Optional[dict[str, Any]]:
        """Get paper details by Semantic Scholar paper ID, DOI, or arXiv ID."""
        params = {"fields": FIELDS}
        try:
            return await self._fetch(f"/paper/{paper_id}", params)
        except Exception as e:
            logger.warning("Semantic Scholar get_paper failed for '%s': %s", paper_id, e)
            return None

    async def batch_references(self, ids: list[str], chunk_size: int = 20) -> list[Optional[dict[str, Any]]]:
        """Fetch papers with their reference lists (one entry per id, None if unknown)."""
        fields = (
            "paperId,externalIds,references.paperId,references.externalIds,references.title,"
            "references.year,references.citationCount,references.authors"
        )
        return await self.batch_papers(ids, fields, chunk_size=chunk_size)

    async def batch_papers(
        self, ids: list[str], fields: str, chunk_size: int = 100
    ) -> list[Optional[dict[str, Any]]]:
        """POST /paper/batch for the given ids.

        Args:
            ids: Semantic Scholar ids or prefixed ids ("DOI:...", "ARXIV:...").
            fields: Comma-separated S2 fields to return.

        Returns:
            One entry per input id (None when S2 does not know the paper or the lookup failed).
        """
        results: list[Optional[dict[str, Any]]] = []
        for i in range(0, len(ids), chunk_size):
            chunk = ids[i : i + chunk_size]
            cache_params = {"ids": chunk, "fields": fields}
            cached = await cache.get("semantic_scholar", "/paper/batch", cache_params)
            if cached is not None:
                results.extend(cached.get("data", []))
                continue
            data: Optional[list] = None
            try:
                resp = await self._request(
                    "POST", "/paper/batch", params={"fields": fields}, json={"ids": chunk}, timeout=30.0
                )
                data = resp.json()
            except Exception as e:
                logger.warning("Semantic Scholar batch lookup failed: %s", e)
            if data is None:
                results.extend([None] * len(chunk))
                continue
            await cache.set("semantic_scholar", "/paper/batch", {"data": data}, params=cache_params)
            results.extend(data)
        return results

    async def close(self) -> None:
        """Close the underlying HTTP client."""
        await self._client.aclose()
