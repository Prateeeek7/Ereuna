"""Unpaywall API client.

Provides legal open-access PDF links for DOIs.
Docs: https://unpaywall.org/products/api
"""

import asyncio
import logging
from typing import Optional

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.clients.cache import cache
from app.config import settings

logger = logging.getLogger(__name__)


class UnpaywallClient:
    """Client for the Unpaywall API."""

    BASE_URL = "https://api.unpaywall.org/v2"

    def __init__(self) -> None:
        self._email = settings.unpaywall_email
        self._client = httpx.AsyncClient(
            base_url=self.BASE_URL,
            timeout=30.0,
            follow_redirects=True,
        )
        self._lock = asyncio.Lock()
        self._last_request_time: float = 0.0

    async def _polite_wait(self) -> None:
        """Enforce 1 req/s polite rate limiting."""
        async with self._lock:
            now = asyncio.get_event_loop().time()
            elapsed = now - self._last_request_time
            if elapsed < 1.0:
                await asyncio.sleep(1.0 - elapsed)
            self._last_request_time = asyncio.get_event_loop().time()

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
    async def get_oa_link(self, doi: str) -> Optional[str]:
        """Get the best open-access PDF URL for a DOI.

        Args:
            doi: The DOI to look up (e.g., "10.1109/TED.2023.1234567").

        Returns:
            URL to the open-access PDF, or None if not available.
        """
        if not doi or not self._email:
            return None

        # Check cache first
        cached = await cache.get("unpaywall", "oa_link", {"doi": doi})
        if cached is not None:
            return cached.get("url")

        await self._polite_wait()

        try:
            resp = await self._client.get(
                f"/{doi}",
                params={"email": self._email},
            )
            resp.raise_for_status()
            data = resp.json()

            # Extract best OA location
            oa_url = None

            # Check best_oa_location first
            best_oa = data.get("best_oa_location") or {}
            if best_oa.get("url_for_pdf"):
                oa_url = best_oa["url_for_pdf"]
            elif best_oa.get("url_for_landing_page"):
                oa_url = best_oa["url_for_landing_page"]

            # Fallback: check oa_locations list
            if not oa_url:
                for loc in data.get("oa_locations") or []:
                    if loc.get("url_for_pdf"):
                        oa_url = loc["url_for_pdf"]
                        break

            # Cache result
            await cache.set("unpaywall", "oa_link", {"url": oa_url}, {"doi": doi})
            return oa_url

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                # DOI not found in Unpaywall — cache the miss
                await cache.set("unpaywall", "oa_link", {"url": None}, {"doi": doi})
                return None
            logger.warning("Unpaywall request failed for DOI %s: %s", doi, e)
            raise
        except Exception as e:
            logger.warning("Unpaywall error for DOI %s: %s", doi, e)
            return None

    async def get_oa_locations(self, doi: str) -> list[str]:
        """All legal open-access URLs Unpaywall knows for a DOI: direct PDF
        links first (best location first), then landing pages."""
        if not doi or not self._email:
            return []

        cached = await cache.get("unpaywall", "oa_locations", {"doi": doi})
        if cached is not None:
            return cached.get("urls", [])

        await self._polite_wait()
        try:
            resp = await self._client.get(f"/{doi}", params={"email": self._email})
            if resp.status_code == 404:
                await cache.set("unpaywall", "oa_locations", {"urls": []}, {"doi": doi})
                return []
            resp.raise_for_status()
            data = resp.json()
        except Exception as e:
            logger.debug("Unpaywall lookup failed for DOI %s: %s", doi, e)
            return []

        locations = [data.get("best_oa_location") or {}] + list(data.get("oa_locations") or [])
        pdfs: list[str] = []
        pages: list[str] = []
        for loc in locations:
            for url, bucket in ((loc.get("url_for_pdf"), pdfs), (loc.get("url_for_landing_page"), pages)):
                if url and url not in pdfs and url not in pages:
                    bucket.append(url)
        urls = pdfs + pages
        await cache.set("unpaywall", "oa_locations", {"urls": urls}, {"doi": doi})
        return urls

    async def close(self) -> None:
        await self._client.aclose()
