"""Bring-your-own-papers mode: reading PDFs, identifying them, private maps."""

import asyncio
import json

import fitz  # PyMuPDF
import pytest
from httpx import ASGITransport, AsyncClient

from app.api import maps as maps_api
from app.api.state import JobEventStream, state_manager
from app.clients import llm as llm_mod
from app.main import app
from app.models.map import GraphData
from app.models.map import MapFilters, ResearchMap
from app.models.paper import Paper
from app.pipeline import uploads
from app.pipeline.uploads import UploadedPdf, paper_from_pdf, read_uploads

TITLE = "Dendrite Suppression in Garnet Solid Electrolytes by Interfacial Engineering"
DOI = "10.1234/jes.2023.5678"
ABSTRACT = (
    "Lithium dendrites limit garnet electrolytes. We coat LLZO pellets with a thin interlayer and "
    "measure the critical current density and ionic conductivity of the coated electrolyte at room "
    "temperature, showing a threefold improvement over bare pellets under identical cycling conditions."
)
BODY = (
    "1. Introduction\nGarnet-type Li7La3Zr2O12 (LLZO) is a promising solid electrolyte.\n"
    "2. Results\nThe coated LLZO reached an ionic conductivity of 0.52 mS cm-1 at 25 °C, "
    "and the critical current density increased to 1.2 mA cm-2.\n"
)


def make_pdf(title: str = TITLE, doi: str | None = DOI, cited_doi: str | None = None) -> bytes:
    doc = fitz.open()
    page = doc.new_page()
    cut = title.rfind(" ", 0, 45)
    page.insert_text((50, 80), title[:cut], fontsize=18)
    page.insert_text((50, 102), title[cut + 1 :], fontsize=18)
    page.insert_text((50, 130), "A. Author, B. Researcher", fontsize=10)
    if doi:
        page.insert_text((50, 145), f"https://doi.org/{doi}", fontsize=8)
    y = 175
    page.insert_text((50, y), "Abstract", fontsize=11)
    for chunk in [ABSTRACT[i : i + 90] for i in range(0, len(ABSTRACT), 90)]:
        y += 14
        page.insert_text((50, y), chunk, fontsize=9)
    for line in BODY.splitlines():
        y += 14
        page.insert_text((50, y), line[:95], fontsize=9)
        if len(line) > 95:
            y += 14
            page.insert_text((50, y), line[95:], fontsize=9)
    if cited_doi:
        page.insert_text((50, 760), f"[1] Other work, doi:{cited_doi}", fontsize=7)
    data = doc.tobytes()
    doc.close()
    return data


def scanned_pdf() -> bytes:
    doc = fitz.open()
    doc.new_page()
    data = doc.tobytes()
    doc.close()
    return data


class StubOpenAlex:
    def __init__(self, works: dict[str, dict] | None = None, search: list[dict] | None = None) -> None:
        self.works = works or {}
        self.search = search or []
        self.looked_up: list[str] = []

    async def get_work(self, ident: str):
        self.looked_up.append(ident)
        return self.works.get(ident)

    async def search_works(self, query: str, per_page: int = 5):
        return self.search

    async def close(self):
        pass


class StubCrossref:
    async def resolve_doi(self, doi: str) -> dict:
        return {}

    async def close(self):
        pass


class StubS2:
    async def match_title(self, title: str):
        return None

    async def close(self):
        pass


class NoGrobid:
    async def process_pdf(self, pdf_bytes: bytes):
        return None

    async def close(self):
        pass


def work(title: str, doi: str) -> dict:
    return {
        "id": "https://openalex.org/W123",
        "title": title,
        "doi": f"https://doi.org/{doi}",
        "publication_year": 2023,
        "primary_location": {"source": {"display_name": "J. Electrochem. Soc."}},
        "cited_by_count": 42,
        "authorships": [{"author": {"display_name": "Ana Moreno"}}],
    }


def test_front_page_gives_title_doi_and_abstract():
    front = uploads._read_front(make_pdf())
    assert front.title == TITLE
    assert uploads._identifiers(front.text)[0] == DOI
    assert uploads._abstract_from_text(front.text).startswith("Lithium dendrites limit garnet")


def test_arxiv_ids_become_dois():
    assert uploads._identifiers("arXiv:2301.01234v2 [cs.LG]") == ["10.48550/arxiv.2301.01234"]


@pytest.mark.asyncio
async def test_pdf_matched_by_its_own_doi():
    openalex = StubOpenAlex(works={f"https://doi.org/{DOI}": work(TITLE, DOI)})
    paper, reason = await paper_from_pdf(UploadedPdf("mine.pdf", make_pdf()), NoGrobid(), openalex, StubCrossref())
    assert reason == ""
    assert paper.source == "upload" and paper.id.startswith("up_")
    assert paper.doi == DOI and paper.year == 2023 and paper.venue == "J. Electrochem. Soc."
    assert paper.short_label == "Moreno '23"
    assert paper.has_full_text and "0.52 mS cm-1" in paper.sections.full_text
    # OpenAlex had no abstract: the one printed in the PDF is used.
    assert paper.abstract.startswith("Lithium dendrites")


@pytest.mark.asyncio
async def test_cited_doi_is_not_taken_for_the_paper():
    # Only a reference's DOI is printed, and it resolves to a different paper.
    other = "10.9999/other.1"
    openalex = StubOpenAlex(works={f"https://doi.org/{other}": work("A Completely Different Study of Catalysis", other)})
    paper, _ = await paper_from_pdf(
        UploadedPdf("mine.pdf", make_pdf(doi=None, cited_doi=other)), NoGrobid(), openalex, StubCrossref()
    )
    assert paper.doi is None
    assert paper.title == TITLE  # from the PDF itself
    assert paper.year == 0 and paper.short_label == "Dendrite Suppression"


@pytest.mark.asyncio
async def test_title_search_used_when_no_doi_printed():
    openalex = StubOpenAlex(search=[work(TITLE, DOI)])
    paper, _ = await paper_from_pdf(UploadedPdf("mine.pdf", make_pdf(doi=None)), NoGrobid(), openalex, StubCrossref())
    assert paper.doi == DOI and paper.citation_count == 42


@pytest.mark.asyncio
async def test_unreadable_and_duplicate_pdfs_are_skipped(monkeypatch):
    monkeypatch.setattr(uploads, "GrobidClient", NoGrobid)
    monkeypatch.setattr(uploads, "OpenAlexClient", StubOpenAlex)
    monkeypatch.setattr(uploads, "CrossrefClient", StubCrossref)
    monkeypatch.setattr(uploads, "SemanticScholarClient", StubS2)
    result = await read_uploads([
        UploadedPdf("a.pdf", make_pdf()),
        UploadedPdf("a-copy.pdf", make_pdf()),
        UploadedPdf("scan.pdf", scanned_pdf()),
        UploadedPdf("notes.pdf", b"hello"),
    ])
    assert [p.title for p in result.papers] == [TITLE]
    reasons = dict(result.skipped)
    assert reasons["a-copy.pdf"] == "same paper uploaded twice"
    assert "scanned" in reasons["scan.pdf"]
    assert reasons["notes.pdf"] == "not a PDF"


@pytest.mark.asyncio
async def test_upload_endpoint_validates_and_starts_a_job(monkeypatch):
    started = {}

    async def fake_pipeline(**kwargs):
        started.update(kwargs)

    monkeypatch.setattr(maps_api, "execute_upload_pipeline", fake_pipeline)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.post(
            "/v1/maps/upload",
            data={"topic": "garnet electrolytes", "include_search": "true"},
            files=[("files", ("notes.txt", b"not a pdf", "application/pdf"))],
        )
        assert resp.status_code == 400 and "not a PDF" in resp.json()["detail"]

        resp = await client.post(
            "/v1/maps/upload",
            data={"topic": "  ", "include_search": "false"},
            files=[("files", ("a.pdf", make_pdf(), "application/pdf"))],
        )
        assert resp.status_code == 400

        resp = await client.post(
            "/v1/maps/upload",
            data={"topic": "garnet electrolytes", "include_search": "true"},
            files=[("files", ("a.pdf", make_pdf(), "application/pdf")), ("files", ("b.pdf", make_pdf(TITLE + " II"), "application/pdf"))],
        )
    assert resp.status_code == 202
    body = resp.json()
    assert body["status"] == "processing" and body["job_id"]
    assert started["include_search"] is True and started["owner_id"] == "usr_test"
    assert [u.filename for u in started["uploads"]] == ["a.pdf", "b.pdf"]


def _private_map(owner: str) -> ResearchMap:
    paper = Paper(id="up_abc", title=TITLE, year=2023, source="upload")
    return ResearchMap(
        id="map_private1", topic="garnet electrolytes", normalized_topic="garnet electrolytes",
        papers=[paper], paper_ids=[paper.id], source="upload", owner_id=owner,
    )


@pytest.mark.asyncio
async def test_uploaded_maps_are_private_and_never_cached():
    await state_manager.store_map(_private_map("usr_someone_else"))
    assert await state_manager.get_cached_map_id("garnet electrolytes", MapFilters()) is None
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get("/v1/maps/map_private1")).status_code == 404
        assert (await client.get("/v1/papers/up_abc")).status_code == 404
        assert (await client.post("/v1/library/maps/map_private1")).status_code == 404

    await state_manager.store_map(_private_map("usr_test"))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/v1/maps/map_private1")
        assert resp.status_code == 200 and resp.json()["source"] == "upload"
        assert (await client.get("/v1/papers/up_abc")).json()["source"] == "upload"
        assert (await client.post("/v1/maps/map_private1/refresh")).status_code == 400


def test_upload_pipeline_builds_a_map_from_the_pdfs(monkeypatch):
    monkeypatch.setattr(llm_mod, "build_providers", lambda: [])
    monkeypatch.setattr(uploads, "GrobidClient", NoGrobid)
    monkeypatch.setattr(uploads, "OpenAlexClient", StubOpenAlex)
    monkeypatch.setattr(uploads, "CrossrefClient", StubCrossref)
    monkeypatch.setattr(uploads, "SemanticScholarClient", StubS2)

    async def no_graph(papers):
        return GraphData()

    monkeypatch.setattr(maps_api, "build_graph", no_graph)

    job = JobEventStream("job_1", "map_up1")
    pdfs = [UploadedPdf("a.pdf", make_pdf()), UploadedPdf("scan.pdf", scanned_pdf())]

    async def run():
        await maps_api.execute_upload_pipeline(
            topic="garnet electrolytes", filters=MapFilters(), map_id="map_up1", job=job,
            uploads=pdfs, include_search=False, owner_id="usr_test",
        )
        return await state_manager.get_map("map_up1", "usr_test")

    research_map = asyncio.run(run())
    events = [(e["event"], json.loads(e["data"])) for e in job.events]
    assert events[-1][0] == "done", events[-1]
    stage_names = [d["name"] for kind, d in events if kind == "stage"]
    assert stage_names[:2] == ["read_uploads", "identify_papers"]
    assert any("Skipped scan.pdf" in d.get("message", "") for kind, d in events if kind == "log")
    assert pdfs == []  # PDF bytes are dropped once parsed

    assert research_map.source == "upload" and research_map.owner_id == "usr_test"
    assert research_map.stats.uploaded_papers == 1 and research_map.stats.full_text_papers == 1
    paper = research_map.papers[0]
    assert paper.sections is None  # full text is not stored
    metrics = {m.key: m for m in paper.extraction.metrics}
    assert metrics["ionic conductivity"].value == pytest.approx(0.52)
