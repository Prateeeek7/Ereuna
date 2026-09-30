"""Crossref API client.

Provides DOI resolution and metadata.
Docs: https://api.crossref.org/
"""

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from app.config import settings


class CrossrefClient:
    """Client for the Crossref API."""

    BASE_URL = "https://api.crossref.org"

    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=self.BASE_URL,
            timeout=30.0,
            headers={"User-Agent": f"Ereuna/0.1 (mailto:{settings.crossref_email})"},
        )

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
    async def resolve_doi(self, doi: str) -> dict:
        """Resolve a DOI to full metadata."""
        clean_doi = doi.strip().removeprefix("https://doi.org/").removeprefix("http://dx.doi.org/")
        try:
            resp = await self._client.get(f"/works/{clean_doi}")
            if resp.status_code == 404:
                return {}
            resp.raise_for_status()
            data = resp.json()
            return data.get("message", {})
        except httpx.HTTPError:
            return {}

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=10))
    async def search_works(self, query: str, rows: int = 50) -> list[dict]:
        """Search Crossref for works matching a query."""
        try:
            resp = await self._client.get("/works", params={"query": query, "rows": rows})
            resp.raise_for_status()
            data = resp.json()
            return data.get("message", {}).get("items", [])
        except httpx.HTTPError:
            return []

    async def close(self) -> None:
        await self._client.aclose()
