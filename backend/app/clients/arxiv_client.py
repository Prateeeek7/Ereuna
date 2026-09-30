"""arXiv API client with Atom XML parsing, caching, and rate limiting.

Provides preprint search, abstracts, and PDF links.
Docs: https://info.arxiv.org/help/api/
"""

import asyncio
import logging
import time
import xml.etree.ElementTree as ET
from typing import Any, Optional

import httpx
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from app.clients.cache import cache

logger = logging.getLogger(__name__)

# Atom XML namespaces
NAMESPACES = {
    "atom": "http://www.w3.org/2005/Atom",
    "arxiv": "http://arxiv.org/schemas/atom",
}


def parse_arxiv_entry(entry: ET.Element) -> dict[str, Any]:
    """Parse a single Atom <entry> XML element into a dictionary."""
    id_elem = entry.find("atom:id", NAMESPACES)
    raw_id = id_elem.text.strip() if id_elem is not None and id_elem.text else ""
    # Extract short arxiv_id e.g. "2105.00002"
    arxiv_id = raw_id.split("/abs/")[-1] if "/abs/" in raw_id else raw_id

    title_elem = entry.find("atom:title", NAMESPACES)
    title = " ".join((title_elem.text or "").split()) if title_elem is not None else ""

    summary_elem = entry.find("atom:summary", NAMESPACES)
    summary = " ".join((summary_elem.text or "").split()) if summary_elem is not None else ""

    published_elem = entry.find("atom:published", NAMESPACES)
    year = 0
    if published_elem is not None and published_elem.text:
        try:
            year = int(published_elem.text.split("-")[0])
        except ValueError:
            pass

    authors = []
    for author_elem in entry.findall("atom:author", NAMESPACES):
        name_elem = author_elem.find("atom:name", NAMESPACES)
        if name_elem is not None and name_elem.text:
            authors.append({"name": name_elem.text.strip()})

    doi_elem = entry.find("arxiv:doi", NAMESPACES)
    doi = doi_elem.text.strip() if doi_elem is not None and doi_elem.text else None

    journal_elem = entry.find("arxiv:journal_ref", NAMESPACES)
    venue = journal_elem.text.strip() if journal_elem is not None and journal_elem.text else "arXiv"

    # Find PDF link
    pdf_url = None
    for link in entry.findall("atom:link", NAMESPACES):
        if link.get("title") == "pdf" or link.get("type") == "application/pdf":
            pdf_url = link.get("href")
            break
    if not pdf_url and arxiv_id:
        pdf_url = f"https://arxiv.org/pdf/{arxiv_id}.pdf"

    return {
        "arxiv_id": arxiv_id,
        "id": f"arxiv_{arxiv_id}",
        "doi": doi,
        "title": title,
        "abstract": summary,
        "year": year,
        "authors": authors,
        "venue": venue,
        "oa_pdf_url": pdf_url,
        "has_full_text": bool(pdf_url),
        "citation_count": 0,
    }


class ArxivClient:
    """Client for the arXiv API."""

    BASE_URL = "https://export.arxiv.org/api"

    def __init__(self, client: Optional[httpx.AsyncClient] = None) -> None:
        self._client = client or httpx.AsyncClient(
            base_url=self.BASE_URL,
            timeout=30.0,
            follow_redirects=True,
            headers={"User-Agent": "Ereuna/0.1 (academic research tool)"},
        )
        self._min_interval = 3.0  # arXiv requests 3 seconds between calls
        self._last_request_time = 0.0
        self._lock = asyncio.Lock()

    async def _throttle(self) -> None:
        """Enforce arXiv 3-second delay policy."""
        async with self._lock:
            now = time.time()
            elapsed = now - self._last_request_time
            if elapsed < self._min_interval:
                await asyncio.sleep(self._min_interval - elapsed)
            self._last_request_time = time.time()

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        retry=retry_if_exception_type((httpx.HTTPError, httpx.TimeoutException)),
        reraise=True,
    )
    async def _fetch_xml(self, query: str, max_results: int) -> str:
        """Fetch raw XML response from arXiv."""
        params = {
            "search_query": f"all:{query}",
            "start": 0,
            "max_results": min(max_results, 100),
            "sortBy": "relevance",
            "sortOrder": "descending",
        }
        cached = await cache.get("arxiv", "/query", params)
        if cached is not None:
            return cached

        await self._throttle()
        resp = await self._client.get("/query", params=params)
        resp.raise_for_status()
        text = resp.text
        await cache.set("arxiv", "/query", text, params=params)
        return text

    async def search(self, query: str, max_results: int = 50) -> list[dict[str, Any]]:
        """Search arXiv for papers matching a query.

        Args:
            query: Free-text search terms.
            max_results: Max papers to return.

        Returns:
            List of parsed paper dictionary objects.
        """
        try:
            xml_text = await self._fetch_xml(query, max_results)
            root = ET.fromstring(xml_text)
            entries = root.findall("atom:entry", NAMESPACES)
            return [parse_arxiv_entry(entry) for entry in entries]
        except Exception as e:
            logger.warning("arXiv search failed for '%s': %s", query, e)
            return []

    async def close(self) -> None:
        """Close the HTTP client."""
        await self._client.aclose()
