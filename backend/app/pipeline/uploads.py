"""Papers from the user's own PDFs.

Each uploaded PDF is parsed (GROBID when running, else PyMuPDF) and identified:
a DOI or arXiv id printed on its first pages is looked up in OpenAlex (then
Crossref); without one, the title from page 1 is searched in OpenAlex. A match
must share the PDF's title, so a DOI cited on page 1 is not mistaken for the
paper's own. Unmatched PDFs keep the title and abstract found in the text.

The PDFs are held in memory only for this step and never written to disk.
"""

import asyncio
import logging
import re
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional

from app.clients.crossref import CrossrefClient
from app.clients.grobid import GrobidClient
from app.clients.openalex import OpenAlexClient
from app.clients.semantic_scholar import SemanticScholarClient
from app.models.paper import Author, Paper, PaperSections
from app.pipeline.dedupe import generate_short_label, normalize_doi, normalize_title, title_similarity
from app.pipeline.fetch_fulltext import _parse_pdf
from app.pipeline.retrieve import openalex_to_paper, semantic_scholar_to_paper

logger = logging.getLogger(__name__)

MAX_UPLOAD_FILES = 15
MAX_UPLOAD_BYTES = 25 * 1024 * 1024
MAX_UPLOAD_TOTAL_BYTES = 120 * 1024 * 1024

# A matched record must have (nearly) the title printed in the PDF.
_TITLE_MATCH = 0.82

_DOI_RE = re.compile(r"\b(10\.\d{4,9}/[^\s\"<>{}]+)", re.IGNORECASE)
_ARXIV_RE = re.compile(r"\barXiv:\s?(\d{4}\.\d{4,5})(?:v\d+)?", re.IGNORECASE)
_ABSTRACT_RE = re.compile(
    r"(?:^|\n)\s*a\s?b\s?s\s?t\s?r\s?a\s?c\s?t\s*[.:—–-]?\s*(.+?)"
    r"(?=\n\s*(?:(?:1|I)\.?\s*)?(?:introduction|keywords|key words|index terms)\b|\Z)",
    re.IGNORECASE | re.DOTALL,
)


@dataclass
class UploadedPdf:
    filename: str
    data: bytes


@dataclass
class UploadResult:
    papers: list[Paper] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)  # (filename, reason)


@dataclass
class _PdfFront:
    """What the first pages say about the paper itself."""

    text: str = ""
    title: str = ""
    meta_title: str = ""
    needs_password: bool = False


def is_pdf(data: bytes) -> bool:
    return data[:1024].lstrip().startswith(b"%PDF-")


def _plausible_title(text: str) -> bool:
    t = " ".join(text.split())
    return 12 <= len(t) <= 300 and " " in t and not re.search(r"\.(pdf|docx?|tex)$|^microsoft|^untitled", t, re.I)


def _read_front(pdf_bytes: bytes) -> _PdfFront:
    """Text of the first two pages and the title (largest type near the top of page 1)."""
    import fitz  # PyMuPDF

    front = _PdfFront()
    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception:
        return front
    try:
        if doc.needs_pass:
            front.needs_password = True
            return front
        front.text = "\n".join(doc[i].get_text("text") for i in range(min(2, len(doc))))
        meta = (doc.metadata or {}).get("title") or ""
        front.meta_title = " ".join(meta.split()) if _plausible_title(meta) else ""
        if len(doc):
            front.title = _title_from_page(doc[0])
    finally:
        doc.close()
    return front


def _title_from_page(page) -> str:
    """The first run of lines set in the largest type in the top part of the page,
    ending at the first paragraph gap (for papers set entirely in one size)."""
    lines: list[tuple[float, float, float, str]] = []  # (size, top, bottom, text)
    for block in page.get_text("dict").get("blocks", []):
        for line in block.get("lines", []):
            spans = [s for s in line.get("spans", []) if s.get("text", "").strip()]
            top, bottom = line["bbox"][1], line["bbox"][3]
            if not spans or top > page.rect.height * 0.6:
                continue
            text = " ".join("".join(s["text"] for s in spans).split())
            if len(re.sub(r"[^A-Za-z]", "", text)) >= 3:
                lines.append((max(s["size"] for s in spans), top, bottom, text))
    if not lines:
        return ""
    lines.sort(key=lambda x: x[1])
    biggest = max(size for size, *_ in lines)
    start = next(i for i, (size, *_) in enumerate(lines) if size >= biggest - 0.6)
    parts: list[str] = []
    prev_bottom = None
    for size, top, bottom, text in lines[start:]:
        if size < biggest - 0.6 or (prev_bottom is not None and top - prev_bottom > size * 0.9):
            break
        parts.append(text)
        prev_bottom = bottom
    title = "\n".join(parts)
    title = re.sub(r"-\s*\n\s*(?=[A-Z0-9])", "-", title)  # "Network-" / "Blocking"
    title = re.sub(r"-\s*\n\s*(?=[a-z])", "", title)  # "electro-" / "lyte"
    title = " ".join(title.split()).rstrip(" .")
    return title if _plausible_title(title) else ""


def _identifiers(front_text: str) -> list[str]:
    """DOIs printed on the first pages, most likely the paper's own first."""
    found: list[str] = []
    for m in _ARXIV_RE.finditer(front_text):
        found.append(f"10.48550/arxiv.{m.group(1)}")
    for m in _DOI_RE.finditer(front_text):
        doi = normalize_doi(m.group(1).rstrip(".,;:)]}'"))
        if doi:
            found.append(doi.lower())
    # Keep order, drop repeats; the paper's own DOI is usually printed first.
    return list(dict.fromkeys(found))[:4]


def _abstract_from_text(text: str) -> str:
    m = _ABSTRACT_RE.search(text[:12000])
    body = " ".join(m.group(1).split()) if m else ""
    if len(body) > 2000:
        body = body[:2000].rsplit(". ", 1)[0] + "."
    return body if len(body) >= 150 else ""


def _title_matches(candidate: str, front: _PdfFront) -> bool:
    """The record's title is the one printed in the PDF."""
    if not candidate:
        return False
    for title in (front.title, front.meta_title):
        if title and title_similarity(candidate, title) >= _TITLE_MATCH:
            return True
    norm = normalize_title(candidate)
    return len(norm) >= 20 and norm in normalize_title(front.text)


def _crossref_to_paper(rec: dict) -> Optional[Paper]:
    title = " ".join(((rec.get("title") or [""])[0]).split())
    if not title:
        return None
    authors = [
        Author(name=" ".join(x for x in (a.get("given"), a.get("family")) if x))
        for a in rec.get("author") or []
        if a.get("family")
    ]
    parts = ((rec.get("issued") or {}).get("date-parts") or [[0]])[0]
    abstract = re.sub(r"<[^>]+>", " ", rec.get("abstract") or "")
    return Paper(
        id="",
        doi=normalize_doi(rec.get("DOI")),
        title=title,
        authors=authors,
        year=int(parts[0] or 0) if parts else 0,
        venue=((rec.get("container-title") or [""])[0]),
        citation_count=rec.get("is-referenced-by-count") or 0,
        abstract=" ".join(re.sub(r"^\s*abstract\s*", "", abstract, flags=re.I).split()),
    )


async def _identify(
    front: _PdfFront,
    openalex: OpenAlexClient,
    crossref: CrossrefClient,
    s2: Optional[SemanticScholarClient] = None,
) -> Optional[Paper]:
    """Published record for the PDF, or None when it can't be matched confidently."""
    for doi in _identifiers(front.text):
        work = await openalex.get_work(f"https://doi.org/{doi}")
        record = openalex_to_paper(work) if work else None
        if record is None:
            rec = await crossref.resolve_doi(doi)
            record = _crossref_to_paper(rec) if rec else None
        if record and _title_matches(record.title, front):
            return record
    for title in dict.fromkeys(t for t in (front.title, front.meta_title) if t):
        for work in await openalex.search_works(title, per_page=5):
            record = openalex_to_paper(work)
            if record and title_similarity(record.title, title) >= _TITLE_MATCH:
                return record
        # Preprints are often missing from OpenAlex search; S2 matches titles directly.
        rec = await s2.match_title(title) if s2 else None
        record = semantic_scholar_to_paper(rec) if rec else None
        if record and title_similarity(record.title, title) >= _TITLE_MATCH:
            arxiv_id = (rec.get("externalIds") or {}).get("ArXiv")
            if not record.doi and arxiv_id:
                record.doi = f"10.48550/arxiv.{arxiv_id}".lower()
            if not record.venue and (record.doi or "").startswith("10.48550/"):
                record.venue = "arXiv"
            return record
    return None


def _label(paper: Paper) -> str:
    if paper.authors:
        return generate_short_label(paper.model_copy(update={"short_label": ""}))
    words = [w for w in re.findall(r"[A-Za-z0-9\-]+", paper.title) if len(w) > 2][:2]
    return " ".join(words)[:22] or "Your paper"


async def paper_from_pdf(
    upload: UploadedPdf,
    grobid: GrobidClient,
    openalex: OpenAlexClient,
    crossref: CrossrefClient,
    s2: Optional[SemanticScholarClient] = None,
) -> tuple[Optional[Paper], str]:
    """(paper, "") or (None, reason it could not be used)."""
    if not is_pdf(upload.data):
        return None, "not a PDF"
    front = await asyncio.to_thread(_read_front, upload.data)
    if front.needs_password:
        return None, "password-protected"
    sections: Optional[PaperSections] = await _parse_pdf(upload.data, grobid)
    if sections is None:
        return None, "no selectable text (scanned pages need OCR first)"

    try:
        record = await _identify(front, openalex, crossref, s2)
    except Exception as e:  # lookups are best effort; the text is what matters
        logger.info("Metadata lookup failed for %s: %s", upload.filename, e)
        record = None

    stem = Path(upload.filename).stem.replace("_", " ").strip()
    base = record or Paper(id="", title=front.title or front.meta_title or stem or "Untitled PDF", year=0)
    abstract = base.abstract or _abstract_from_text(sections.full_text)
    paper = base.model_copy(update={
        "id": f"up_{uuid.uuid4().hex[:12]}",
        "abstract": abstract,
        "oa_pdf_url": None,
        "has_full_text": True,
        "sections": sections,
        "source": "upload",
        "relevance_score": 1.0,
        "relevance_reason": "You uploaded this paper.",
        "short_label": "",
    })
    paper.short_label = _label(paper)
    return paper, ""


async def read_uploads(
    uploads: list[UploadedPdf],
    on_progress: Optional[Callable[[str], None]] = None,
) -> UploadResult:
    """Parse and identify each PDF; duplicates (same DOI or title) are kept once."""
    grobid, openalex, crossref, s2 = GrobidClient(), OpenAlexClient(), CrossrefClient(), SemanticScholarClient()
    result = UploadResult()
    try:
        outcomes = await asyncio.gather(*(paper_from_pdf(u, grobid, openalex, crossref, s2) for u in uploads))
    finally:
        await asyncio.gather(grobid.close(), openalex.close(), crossref.close(), s2.close(), return_exceptions=True)

    seen: set[str] = set()
    for upload, (paper, reason) in zip(uploads, outcomes):
        if paper is None:
            result.skipped.append((upload.filename, reason))
            continue
        keys = {k for k in ((paper.doi or "").lower(), normalize_title(paper.title)) if k}
        if keys & seen:
            result.skipped.append((upload.filename, "same paper uploaded twice"))
            continue
        seen |= keys
        result.papers.append(paper)
        if on_progress:
            where = f"matched to {paper.venue or 'its published record'}" if paper.doi else "read from the PDF only"
            on_progress(f"{upload.filename}: “{paper.title[:90]}” — {where}.")
    return result
