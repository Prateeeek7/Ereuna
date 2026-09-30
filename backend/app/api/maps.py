"""Maps API routes — creation, retrieval, analysis, and background pipeline execution."""

import asyncio
import csv
import datetime
import io
import logging
import re
import uuid
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, File, Form, HTTPException, Query, Response, UploadFile
from fastapi.responses import PlainTextResponse

from app.api.state import JobEventStream, state_manager
from app.auth import CurrentUser, User
from app.config import settings
from app.clients.llm import LlmClient
from app.models.paper import Paper
from app.models.map import (
    CreateMapRequest,
    CreateMapResponse,
    ExportRequest,
    ExportResponse,
    GraphData,
    MapFilters,
    MapStats,
    RefreshMapResponse,
    ResearchMap,
)
from app.pipeline.analyze import analyze
from app.pipeline.expand_query import expand_query
from app.pipeline.experiments import suggest_experiments
from app.pipeline.extract import extract
from app.pipeline.fetch_fulltext import fetch_fulltext
from app.pipeline.graph import build_graph
from app.pipeline.rank import rank_and_filter_papers
from app.pipeline.retrieve import retrieve_candidates
from app.pipeline.dedupe import title_similarity
from app.pipeline.uploads import (
    MAX_UPLOAD_BYTES,
    MAX_UPLOAD_FILES,
    MAX_UPLOAD_TOTAL_BYTES,
    UploadedPdf,
    is_pdf,
    read_uploads,
)

logger = logging.getLogger(__name__)

router = APIRouter()


def _disambiguate_labels(papers: list) -> None:
    """Make short labels unique within a map ("Zhang '24a", "Zhang '24b")."""
    counts: dict[str, int] = {}
    for p in papers:
        counts[p.short_label] = counts.get(p.short_label, 0) + 1
    seen: dict[str, int] = {}
    for p in papers:
        if counts.get(p.short_label, 0) > 1:
            idx = seen.get(p.short_label, 0)
            seen[p.short_label] = idx + 1
            p.short_label = f"{p.short_label}{chr(ord('a') + idx)}"


def _log(job: JobEventStream, message: str) -> None:
    job.add_event("log", {"message": message})


def _stage(job: JobEventStream, number: int, name: str, progress: float) -> None:
    job.add_event("stage", {"stage": number, "name": name, "progress": progress, "status": "running"})


def _announce_llm(job: JobEventStream, llm: LlmClient) -> None:
    if llm.is_configured():
        _log(job, f"Language models, in order: {llm.describe()}.")
    else:
        _log(job, (
            "No language model configured: using conservative text heuristics. "
            "Gap synthesis and experiment suggestions are disabled."
        ))


async def _search_papers(
    topic: str,
    filters: MapFilters,
    job: JobEventStream,
    llm: LlmClient,
    max_papers: int,
    exclude: Optional[list[Paper]] = None,
    stages: tuple[tuple[int, str, float], ...] = (
        (1, "expand_query", 0.05), (2, "retrieve", 0.20), (3, "rank", 0.35), (4, "fetch_fulltext", 0.50),
    ),
) -> Optional[list[Paper]]:
    """Stages 1–4: find, rank and fetch the literature for a topic.

    Papers matching one in `exclude` (by DOI or title) are left out. `stages` gives
    the (number, name, progress) announced for each step. Returns None when no source
    returned anything (an error event has been sent).
    """
    # Stage 1: Query expansion
    _stage(job, *stages[0])
    _log(job, f"Analyzing research topic: '{topic}'")
    queries = await expand_query(topic, llm_client=llm)
    _log(job, f"Generated {len(queries)} search queries: " + " | ".join(queries))

    # Stage 2: Parallel retrieval
    _stage(job, *stages[1])
    _log(job, "Querying OpenAlex, Semantic Scholar, and arXiv in parallel...")
    candidates = await retrieve_candidates(queries)
    if exclude:
        dois = {p.doi.lower() for p in exclude if p.doi}
        titles = [p.title for p in exclude]
        candidates = [
            c for c in candidates
            if not (c.doi and c.doi.lower() in dois)
            and not any(title_similarity(c.title, t) >= 0.92 for t in titles)
        ]
    if not candidates:
        if exclude:
            _log(job, "No related papers found; continuing with your papers only.")
            return []
        job.add_event("error", {
            "error": "no_papers_found",
            "message": (
                "No papers were returned by OpenAlex, Semantic Scholar or arXiv. "
                "Check the server's internet connection or try a broader topic."
            ),
        })
        return None
    _log(job, f"Retrieved {len(candidates)} unique papers after DOI and title deduplication.")

    # Stage 3: Multi-factor ranking
    _stage(job, *stages[2])
    _log(job, "Computing composite relevance scores and filtering candidates...")
    # Embedding similarity is CPU-bound; keep the event loop (and SSE) responsive.
    ranked_papers = await asyncio.to_thread(
        rank_and_filter_papers,
        topic=topic,
        papers=candidates,
        filters=filters.model_copy(update={"max_papers": max_papers}),
        max_papers=max_papers,
    )
    for p in ranked_papers[:15]:
        job.add_event("paper_selected", {
            "paper_id": p.id,
            "title": p.title,
            "short_label": p.short_label,
            "score": p.relevance_score,
            "citations": p.citation_count,
            "year": p.year,
        })
    _log(job, f"Selected top {len(ranked_papers)} papers for extraction.")

    # Stage 4: Fetch full text
    _stage(job, *stages[3])
    return await fetch_fulltext(ranked_papers, on_progress=lambda msg: _log(job, msg))


async def _analyze_and_store(
    topic: str,
    filters: MapFilters,
    map_id: str,
    job: JobEventStream,
    llm: LlmClient,
    papers: list[Paper],
    source: str = "search",
    owner_id: Optional[str] = None,
    save_for: Optional[str] = None,
) -> None:
    """Stages 5–8: extract, compare, graph and suggest, then store the map.

    `save_for` is the user who asked for the map; it goes into their library.
    """
    _disambiguate_labels(papers)
    full_text_count = sum(1 for p in papers if p.has_full_text)
    abstract_count = len(papers) - full_text_count

    def progress(msg: str) -> None:
        _log(job, msg)

    # Stage 5: Structured extraction & evidence verification
    _stage(job, 5, "extract", 0.65)
    extracted_papers, reports = await extract(papers, on_progress=progress)

    total_items = sum(r.total_items for r in reports)
    verified_items = sum(r.verified_items for r in reports)
    pass_rate = (verified_items / total_items * 100) if total_items > 0 else 0.0

    # Stage 6: Cross-paper analysis
    _stage(job, 6, "cross_paper", 0.78)
    _log(job, "Cross-paper analysis: comparing reported metrics and stated limitations...")
    analysis_res = await analyze(extracted_papers, topic, llm=llm)
    contradictions = analysis_res["contradictions"]
    gaps = analysis_res["gaps"]
    tools = analysis_res["tools"]
    findings = analysis_res["findings"]
    synthesis = analysis_res["synthesis"]
    _log(job, (
        f"Analysis found {len(findings)} verified findings, {len(contradictions)} divergent "
        f"results, {len(gaps)} research gaps, and {len(tools)} tools/datasets."
    ))

    # Stage 7: Citation Graph & Server-side Layout
    _stage(job, 7, "citation_graph", 0.88)
    _log(job, "Building citation graph from OpenAlex reference lists...")
    graph_data = await build_graph(extracted_papers)
    map_ids = {p.id for p in extracted_papers}
    in_map_edges = sum(1 for e in graph_data.edges if e.target in map_ids)
    _log(job, (
        f"Graph constructed: {len(graph_data.nodes)} nodes, {len(graph_data.edges)} citation edges "
        f"({in_map_edges} between papers in this map)."
    ))

    # Stage 8: Experiment suggestions
    _stage(job, 8, "experiments", 0.95)
    if llm.is_configured() and gaps:
        _log(job, "Drafting experiment suggestions for the identified gaps...")
    experiments = await suggest_experiments(gaps, extracted_papers, tools=tools, topic=topic, llm=llm)
    if experiments:
        _log(job, f"Drafted {len(experiments)} experiment suggestions.")

    valid_years = [p.year for p in extracted_papers if p.year > 0]
    stats = MapStats(
        total_papers=len(extracted_papers),
        full_text_papers=full_text_count,
        abstract_only_papers=abstract_count,
        year_range=[min(valid_years), max(valid_years)] if valid_years else [],
        gaps_count=len(gaps),
        contradictions_count=len(contradictions),
        uploaded_papers=sum(1 for p in extracted_papers if p.source == "upload"),
    )

    now = datetime.datetime.now(datetime.timezone.utc)
    research_map = ResearchMap(
        id=map_id,
        topic=topic,
        normalized_topic=" ".join(topic.strip().lower().split()),
        filters=filters,
        created_at=now,
        updated_at=now,
        stats=stats,
        synthesis=synthesis,
        paper_ids=[p.id for p in extracted_papers],
        papers=extracted_papers,
        findings=findings,
        gaps=gaps,
        contradictions=contradictions,
        experiments=experiments,
        tools=tools,
        graph=graph_data,
        source=source,
        owner_id=owner_id,
    )
    await state_manager.store_map(research_map, filters)
    await _add_to_library(save_for, map_id)

    job.add_event("stage", {"stage": 8, "name": "completed", "progress": 1.0, "status": "completed"})
    job.add_event("done", {
        "map_id": map_id,
        "papers_count": len(extracted_papers),
        "full_text_count": full_text_count,
        "gaps_count": len(gaps),
        "contradictions_count": len(contradictions),
        "experiments_count": len(experiments),
        "verification_pass_rate": round(pass_rate, 1),
    })
    logger.info(
        "Generated %s map %s: %d papers, %d gaps, %d contradictions, %d experiments.",
        source, map_id, len(extracted_papers), len(gaps), len(contradictions), len(experiments),
    )


async def _add_to_library(user_id: Optional[str], map_id: str) -> None:
    """Every map a user builds or opens from a search lands in their library."""
    if not user_id:
        return
    try:
        await state_manager.save_map(user_id, map_id)
    except Exception as e:  # the map itself is fine; don't fail the job over this
        logger.warning("Could not add map %s to the library of %s: %s", map_id, user_id, e)


async def execute_retrieval_pipeline(
    topic: str,
    filters: MapFilters,
    map_id: str,
    job: JobEventStream,
    user_id: Optional[str] = None,
) -> None:
    """Search mode: pipeline stages 1–8 with real-time SSE progress events."""
    llm = LlmClient()
    try:
        _announce_llm(job, llm)
        papers = await _search_papers(topic, filters, job, llm, max_papers=filters.max_papers)
        if papers is None:
            return
        await _analyze_and_store(topic, filters, map_id, job, llm, papers, save_for=user_id)
    except Exception as e:
        logger.exception("Pipeline execution failed for map %s: %s", map_id, e)
        job.add_event("error", {"error": str(e), "message": "Failed to complete pipeline"})
    finally:
        await llm.close()


# Stage names in upload mode; the app labels stages by these names.
UPLOAD_STAGES = ("read_uploads", "identify_papers", "find_related", "fetch_related")


async def execute_upload_pipeline(
    topic: str,
    filters: MapFilters,
    map_id: str,
    job: JobEventStream,
    uploads: list[UploadedPdf],
    include_search: bool,
    owner_id: str,
) -> None:
    """Upload mode: the user's PDFs (optionally plus related literature) through stages 5–8."""
    llm = LlmClient()
    try:
        _announce_llm(job, llm)
        _stage(job, 1, UPLOAD_STAGES[0], 0.05)
        _log(job, f"Reading {len(uploads)} PDF{'s' if len(uploads) != 1 else ''} for '{topic}'...")
        _stage(job, 2, UPLOAD_STAGES[1], 0.15)
        result = await read_uploads(uploads, on_progress=lambda msg: _log(job, msg))
        uploads.clear()  # drop the PDF bytes as soon as they are parsed
        for filename, reason in result.skipped:
            _log(job, f"Skipped {filename}: {reason}.")
        if not result.papers:
            job.add_event("error", {
                "error": "no_readable_pdfs",
                "message": "None of the PDFs had readable text. Scanned pages need OCR before uploading.",
            })
            return
        for p in result.papers:
            job.add_event("paper_selected", {
                "paper_id": p.id,
                "title": p.title,
                "short_label": p.short_label,
                "score": 1.0,
                "citations": p.citation_count,
                "year": p.year,
            })
        _log(job, f"Read {len(result.papers)} of your papers in full.")

        papers = list(result.papers)
        if include_search:
            related_budget = max(5, filters.max_papers - len(papers))
            related = await _search_papers(
                topic, filters, job, llm,
                max_papers=related_budget,
                exclude=result.papers,
                stages=((3, "find_related", 0.25), (3, "find_related", 0.32), (3, "find_related", 0.40), (4, "fetch_related", 0.50)),
            )
            papers += related or []
        else:
            _stage(job, 3, UPLOAD_STAGES[2], 0.40)
            _log(job, "Using only your papers.")
        await _analyze_and_store(topic, filters, map_id, job, llm, papers, source="upload", owner_id=owner_id, save_for=owner_id)
    except Exception as e:
        logger.exception("Upload pipeline failed for map %s: %s", map_id, e)
        job.add_event("error", {"error": str(e), "message": "Failed to complete pipeline"})
    finally:
        await llm.close()


# Backwards-compatible name used by the Arq worker.
async def _run_pipeline_task(job_id: str, topic: str, filters: MapFilters, map_id: str, job: JobEventStream) -> None:
    await execute_retrieval_pipeline(topic=topic, filters=filters, map_id=map_id, job=job)


@router.post("/maps", response_model=CreateMapResponse, status_code=202)
async def create_map(
    request: CreateMapRequest,
    background_tasks: BackgroundTasks,
    user: User = CurrentUser,
) -> CreateMapResponse:
    """Create a new research map or return a cached one.

    If a map for the same normalized topic and filters exists, returns status 'ready'.
    Otherwise, starts pipeline stages 1–8 in the background and returns status 'processing'.
    """
    topic = request.topic.strip()
    if not topic:
        raise HTTPException(status_code=400, detail="Topic must not be empty")

    filters = request.filters

    # Check cache
    cached_map_id = await state_manager.get_cached_map_id(topic, filters)
    if cached_map_id:
        await _add_to_library(user.id, cached_map_id)
        return CreateMapResponse(
            map_id=cached_map_id,
            status="ready",
        )

    await _check_daily_limit(user)

    # Generate new IDs
    map_id = f"map_{uuid.uuid4().hex[:12]}"
    job_id = f"job_{uuid.uuid4().hex[:12]}"

    # Initialize job event stream
    job = state_manager.create_job(job_id=job_id, map_id=map_id)

    # Run pipeline in background
    background_tasks.add_task(
        execute_retrieval_pipeline,
        topic=topic,
        filters=filters,
        map_id=map_id,
        job=job,
        user_id=user.id,
    )

    return CreateMapResponse(
        map_id=map_id,
        job_id=job_id,
        status="processing",
    )


async def _check_daily_limit(user: User) -> None:
    limit = settings.max_maps_per_user_per_day
    if limit > 0 and await state_manager.maps_created_today(user.id) >= limit:
        raise HTTPException(
            status_code=429,
            detail=f"Daily limit reached: {limit} new maps per day. Cached topics still open instantly.",
        )
    await state_manager.record_map_created(user.id)


@router.post("/maps/upload", response_model=CreateMapResponse, status_code=202)
async def create_map_from_uploads(
    background_tasks: BackgroundTasks,
    topic: str = Form(...),
    include_search: bool = Form(False),
    files: list[UploadFile] = File(...),
    user: User = CurrentUser,
) -> CreateMapResponse:
    """Build a private map from the user's own PDFs (multipart: topic, include_search, files).

    The PDFs are read into memory, parsed, and discarded; only the resulting map is stored.
    """
    topic = " ".join(topic.split())
    if not topic:
        raise HTTPException(status_code=400, detail="Topic must not be empty")
    if len(topic) > 300:
        raise HTTPException(status_code=400, detail="Topic is too long (300 characters at most)")
    if not files:
        raise HTTPException(status_code=400, detail="Add at least one PDF")
    if len(files) > MAX_UPLOAD_FILES:
        raise HTTPException(status_code=400, detail=f"Up to {MAX_UPLOAD_FILES} PDFs per map")

    uploads: list[UploadedPdf] = []
    total = 0
    for f in files:
        name = (f.filename or "paper.pdf").rsplit("/", 1)[-1][:120]
        data = await f.read(MAX_UPLOAD_BYTES + 1)
        if len(data) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail=f"{name} is larger than {MAX_UPLOAD_BYTES // (1024 * 1024)} MB")
        if not is_pdf(data):
            raise HTTPException(status_code=400, detail=f"{name} is not a PDF")
        total += len(data)
        if total > MAX_UPLOAD_TOTAL_BYTES:
            raise HTTPException(status_code=413, detail="The PDFs are too large together; upload fewer at a time")
        uploads.append(UploadedPdf(filename=name, data=data))

    await _check_daily_limit(user)

    map_id = f"map_{uuid.uuid4().hex[:12]}"
    job_id = f"job_{uuid.uuid4().hex[:12]}"
    job = state_manager.create_job(job_id=job_id, map_id=map_id)
    background_tasks.add_task(
        execute_upload_pipeline,
        topic=topic,
        filters=MapFilters(),
        map_id=map_id,
        job=job,
        uploads=uploads,
        include_search=include_search,
        owner_id=user.id,
    )
    return CreateMapResponse(map_id=map_id, job_id=job_id, status="processing")


@router.get("/maps/{map_id}", response_model=ResearchMap)
async def get_map(map_id: str, user: User = CurrentUser) -> ResearchMap:
    """Retrieve a complete research map with all associated data."""
    research_map = await state_manager.get_map(map_id, user.id)
    if not research_map:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")
    return research_map


@router.post("/maps/{map_id}/refresh", response_model=RefreshMapResponse)
async def refresh_map(
    map_id: str,
    background_tasks: BackgroundTasks,
    user: User = CurrentUser,
) -> RefreshMapResponse:
    """Re-run retrieval for new papers on an existing map."""
    research_map = await state_manager.get_map(map_id, user.id)
    if not research_map:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")
    if research_map.source == "upload":
        raise HTTPException(status_code=400, detail="Maps built from your PDFs can't be refreshed; upload the papers again.")

    job_id = f"job_{uuid.uuid4().hex[:12]}"
    job = state_manager.create_job(job_id=job_id, map_id=map_id)

    background_tasks.add_task(
        execute_retrieval_pipeline,
        topic=research_map.topic,
        filters=research_map.filters,
        map_id=map_id,
        job=job,
    )

    return RefreshMapResponse(
        map_id=map_id,
        job_id=job_id,
        status="processing",
    )


@router.get("/maps/{map_id}/graph", response_model=GraphData)
async def get_map_graph(map_id: str, user: User = CurrentUser) -> GraphData:
    """Retrieve the citation graph with precomputed server-side force layout."""
    research_map = await state_manager.get_map(map_id, user.id)
    if not research_map:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")

    if research_map.graph:
        return research_map.graph

    # Fallback compute if map was created before graph stage
    graph = await build_graph(research_map.papers)
    research_map.graph = graph
    await state_manager.store_map(research_map)
    return graph


def _generate_markdown_export(m: ResearchMap) -> str:
    """Markdown export: synthesis, per-paper extraction with quotes, analysis, bibliography."""
    labels = {p.id: p.short_label or p.title[:30] for p in m.papers}
    created = m.created_at.strftime("%Y-%m-%d %H:%M UTC") if isinstance(m.created_at, datetime.datetime) else str(m.created_at)
    year_range = f"{m.stats.year_range[0]}–{m.stats.year_range[1]}" if len(m.stats.year_range) == 2 else "n/a"
    lines = [
        f"# Research Map: {m.topic}",
        f"*Generated {created} by Ereuna*",
        "",
        "## Corpus",
        f"- Papers: {len(m.papers)} ({m.stats.full_text_papers} full text, {m.stats.abstract_only_papers} abstract only)",
        f"- Publication years: {year_range}",
        f"- Research gaps: {len(m.gaps)} · Divergent results: {len(m.contradictions)} · Experiment suggestions: {len(m.experiments)}",
        "",
    ]
    if m.synthesis.text:
        lines += ["## Synthesis", "", m.synthesis.text, ""]

    lines += ["## Papers", ""]
    for p in m.papers:
        authors = ", ".join(a.name for a in p.authors[:3]) + (" et al." if len(p.authors) > 3 else "")
        lines.append(f"### {labels[p.id]} — {p.title}")
        meta = " · ".join(x for x in [authors, str(p.year) if p.year else "", p.venue, f"{p.citation_count} citations"] if x)
        lines.append(f"*{meta}*")
        ext = p.extraction
        if ext:
            if ext.problem:
                lines.append(f"- **Problem**: {ext.problem.text}")
            if ext.method:
                lines.append(f"- **Method**: {ext.method.text}")
            if ext.technology:
                t = ext.technology
                tech = ", ".join(x for x in [f"{t.node_nm} nm" if t.node_nm else "", t.device or "", t.cell_type or ""] if x)
                if tech:
                    lines.append(f"- **Technology**: {tech}")
            for f in ext.findings:
                lines.append(f"- **Finding**: {f.text}")
                lines.append(f"  > {f.evidence.quote}")
            if ext.metrics:
                lines.append("- **Metrics**:")
                for met in ext.metrics:
                    cond = []
                    if met.conditions.vdd is not None:
                        cond.append(f"{met.conditions.vdd:g} V")
                    if met.conditions.temp_c is not None:
                        cond.append(f"{met.conditions.temp_c:g} °C")
                    if met.conditions.corner:
                        cond.append(met.conditions.corner)
                    cond_str = f" ({', '.join(cond)})" if cond else ""
                    lines.append(f"  - {met.name}: {met.value:g} {met.unit}{cond_str}")
            for lim in ext.limitations:
                lines.append(f"- **Limitation**: {lim.text}")
        lines.append("")

    if m.contradictions:
        lines += ["## Divergent Results", ""]
        for c in m.contradictions:
            lines.append(f"### {c.metric}")
            for e in c.entries:
                cond = ", ".join(f"{k}: {v}" for k, v in e.conditions.items() if v)
                lines.append(f"- {labels.get(e.paper_id, e.paper_id)}: {e.value:g} {e.unit}" + (f" ({cond})" if cond else ""))
            lines.append(f"> {c.likely_reason}")
            lines.append("")

    if m.gaps:
        lines += ["## Research Gaps", ""]
        for g in m.gaps:
            lines.append(f"### {g.statement}")
            lines.append(f"- Evidence: {g.pattern}")
            if g.why_it_matters:
                lines.append(f"- Why it matters: {g.why_it_matters}")
            lines.append(f"- Papers: {', '.join(labels.get(pid, pid) for pid in g.supporting_paper_ids)}")
            lines.append("")

    if m.experiments:
        lines += ["## Experiment Suggestions (model-generated)", ""]
        for exp in m.experiments:
            lines.append(f"### {exp.hypothesis}")
            if exp.setup:
                lines.append(f"- Setup: {exp.setup}")
            if exp.tools:
                lines.append(f"- Tools: {', '.join(exp.tools)}")
            if exp.variables:
                lines.append(f"- Variables: {'; '.join(exp.variables)}")
            if exp.expected_result:
                lines.append(f"- Measure: {exp.expected_result}")
            lines.append(f"- Difficulty: {exp.difficulty}")
            lines.append("")

    lines += ["## Bibliography", ""]
    for i, p in enumerate(m.papers, start=1):
        authors = ", ".join(a.name for a in p.authors)
        ref = f"{i}. {authors + '. ' if authors else ''}{p.title}."
        if p.venue:
            ref += f" *{p.venue}*"
        if p.year:
            ref += f" ({p.year})"
        if p.doi:
            ref += f". https://doi.org/{p.doi}"
        lines.append(ref)

    return "\n".join(lines) + "\n"


_BIBTEX_SPECIAL = {"\\": "\\textbackslash{}", "{": "\\{", "}": "\\}", "&": "\\&", "%": "\\%", "#": "\\#", "_": "\\_"}


def _bibtex_escape(text: str) -> str:
    return re.sub(r"[\\{}&%#_]", lambda m: _BIBTEX_SPECIAL[m.group(0)], text)


def _generate_bibtex_export(m: ResearchMap) -> str:
    """BibTeX entries for all papers in the map, with unique citation keys."""
    entries = []
    used: set[str] = set()
    for p in m.papers:
        surname = re.sub(r"[^A-Za-z0-9]", "", p.authors[0].name.split()[-1]) if p.authors else "anon"
        first_word = next((w for w in re.findall(r"[A-Za-z]+", p.title) if len(w) > 3), "paper")
        base = f"{surname.lower()}{p.year or ''}{first_word.lower()}"
        key, n = base, 1
        while key in used:
            n += 1
            key = f"{base}{n}"
        used.add(key)

        is_preprint = "arxiv" in (p.venue or "").lower() or p.id.startswith("arxiv_")
        fields = [("title", f"{{{_bibtex_escape(p.title)}}}")]
        if p.authors:
            fields.append(("author", " and ".join(_bibtex_escape(a.name) for a in p.authors)))
        if p.year:
            fields.append(("year", str(p.year)))
        if p.venue and not is_preprint:
            fields.append(("journal", _bibtex_escape(p.venue)))
        if is_preprint and p.id.startswith("arxiv_"):
            fields.append(("eprint", p.id[len("arxiv_"):]))
            fields.append(("archivePrefix", "arXiv"))
        if p.doi:
            fields.append(("doi", p.doi))
        if p.oa_pdf_url:
            fields.append(("url", p.oa_pdf_url))
        body = ",\n".join(f"  {k} = {{{v}}}" for k, v in fields)
        entries.append(f"@{'misc' if is_preprint else 'article'}{{{key},\n{body}\n}}")
    return "\n\n".join(entries) + "\n"


def _generate_csv_export(m: ResearchMap) -> str:
    """CSV of papers and key attributes."""
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow([
        "id", "short_label", "title", "authors", "year", "venue", "citations", "doi",
        "has_full_text", "relevance_score", "findings", "metrics", "url",
    ])
    for p in m.papers:
        ext = p.extraction
        writer.writerow([
            p.id,
            p.short_label,
            p.title,
            "; ".join(a.name for a in p.authors),
            p.year or "",
            p.venue,
            p.citation_count,
            p.doi or "",
            p.has_full_text,
            p.relevance_score,
            len(ext.findings) if ext else 0,
            "; ".join(f"{x.name}={x.value:g} {x.unit}".strip() for x in ext.metrics) if ext else "",
            p.oa_pdf_url or (f"https://doi.org/{p.doi}" if p.doi else ""),
        ])
    return buf.getvalue()


@router.post("/maps/{map_id}/export", response_model=ExportResponse)
async def export_map(map_id: str, request: ExportRequest, user: User = CurrentUser) -> ExportResponse:
    """Export a research map in the specified format (md, bibtex, csv)."""
    research_map = await state_manager.get_map(map_id, user.id)
    if not research_map:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")

    fmt = request.format.lower().strip()
    if fmt not in ("md", "markdown", "bibtex", "csv"):
        raise HTTPException(status_code=400, detail=f"Unsupported format '{fmt}'. Choose 'md', 'bibtex', or 'csv'.")

    download_url = f"/v1/maps/{map_id}/download?format={fmt}"
    expires_at = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=24)

    return ExportResponse(
        download_url=download_url,
        format=fmt,
        expires_at=expires_at,
    )


@router.get("/maps/{map_id}/download")
async def download_map(map_id: str, format: str = Query("md"), user: User = CurrentUser) -> Response:
    """Download the exported file directly."""
    research_map = await state_manager.get_map(map_id, user.id)
    if not research_map:
        raise HTTPException(status_code=404, detail=f"Research map '{map_id}' not found")

    fmt = format.lower().strip()
    safe_topic = "".join(c if c.isalnum() else "_" for c in research_map.topic[:30])

    if fmt in ("md", "markdown"):
        content = _generate_markdown_export(research_map)
        return PlainTextResponse(
            content=content,
            media_type="text/markdown",
            headers={"Content-Disposition": f"attachment; filename=ereuna_{safe_topic}.md"},
        )
    elif fmt == "bibtex":
        content = _generate_bibtex_export(research_map)
        return PlainTextResponse(
            content=content,
            media_type="application/x-bibtex",
            headers={"Content-Disposition": f"attachment; filename=ereuna_{safe_topic}.bib"},
        )
    elif fmt == "csv":
        content = _generate_csv_export(research_map)
        return PlainTextResponse(
            content=content,
            media_type="text/csv",
            headers={"Content-Disposition": f"attachment; filename=ereuna_{safe_topic}.csv"},
        )
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported format '{fmt}'")
