"""Stage 7: Graph — build citation network and compute force-directed layout."""

import hashlib
import logging
import math
import re
from collections import Counter
from typing import Any, Optional

from app.clients.openalex import OpenAlexClient
from app.clients.semantic_scholar import SemanticScholarClient
from app.models.map import GraphData, GraphEdge, GraphNode
from app.models.paper import Paper

logger = logging.getLogger(__name__)


def _deterministic_seed(text: str) -> float:
    """Generate a deterministic float in [0, 1) from text."""
    h = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return int(h[:8], 16) / 0xFFFFFFFF


def compute_force_directed_layout(
    nodes: list[GraphNode],
    edges: list[GraphEdge],
    width: float = 1000.0,
    height: float = 1000.0,
    iterations: int = 80,
) -> None:
    """Compute 2D Fruchterman-Reingold force-directed layout server-side.

    Updates node x, y coordinates in place within [60, width-60] x [60, height-60].
    """
    n = len(nodes)
    if n == 0:
        return

    if n == 1:
        nodes[0].x = width / 2.0
        nodes[0].y = height / 2.0
        return

    # Initial deterministic placement (circle with perturbation based on hash)
    center_x = width / 2.0
    center_y = height / 2.0
    radius = min(width, height) * 0.35

    for i, node in enumerate(nodes):
        angle = (2.0 * math.pi * i) / n
        jitter = (_deterministic_seed(node.paper_id) - 0.5) * 40.0
        node.x = center_x + (radius + jitter) * math.cos(angle)
        node.y = center_y + (radius + jitter) * math.sin(angle)

    area = (width - 120.0) * (height - 120.0)
    k = 0.75 * math.sqrt(area / max(1, n))
    k_sq = k * k

    # Map node id to index
    id_to_idx = {node.paper_id: i for i, node in enumerate(nodes)}

    # Papers with no citation links would otherwise be pushed to the canvas
    # corners by repulsion; pull them in harder so they sit around the cluster.
    degree = [0] * n
    for edge in edges:
        for key in (edge.source, edge.target):
            if key in id_to_idx:
                degree[id_to_idx[key]] += 1
    gravity = [0.06 if d > 0 else 0.22 for d in degree]

    # Temperature cools over iterations
    temp = width / 10.0
    cooling = temp / (iterations + 1)

    for it in range(iterations):
        disp_x = [0.0] * n
        disp_y = [0.0] * n

        # 1. Repulsive forces between all node pairs
        for i in range(n):
            for j in range(i + 1, n):
                dx = nodes[i].x - nodes[j].x
                dy = nodes[i].y - nodes[j].y
                dist = math.hypot(dx, dy)
                if dist < 0.01:
                    dx = 0.01 * (_deterministic_seed(f"{i}_{j}") - 0.5)
                    dy = 0.01
                    dist = math.hypot(dx, dy)

                force = k_sq / dist
                fx = (dx / dist) * force
                fy = (dy / dist) * force

                disp_x[i] += fx
                disp_y[i] += fy
                disp_x[j] -= fx
                disp_y[j] -= fy

        # 2. Attractive forces along edges
        for edge in edges:
            u = id_to_idx.get(edge.source)
            v = id_to_idx.get(edge.target)
            if u is None or v is None or u == v:
                continue

            dx = nodes[u].x - nodes[v].x
            dy = nodes[u].y - nodes[v].y
            dist = math.hypot(dx, dy)
            if dist < 0.01:
                continue

            force = (dist * dist) / k * edge.weight
            fx = (dx / dist) * force
            fy = (dy / dist) * force

            disp_x[u] -= fx
            disp_y[u] -= fy
            disp_x[v] += fx
            disp_y[v] += fy

        # 3. Center gravity to keep graph cohesive
        for i in range(n):
            disp_x[i] += (center_x - nodes[i].x) * gravity[i]
            disp_y[i] += (center_y - nodes[i].y) * gravity[i]

        # 4. Apply displacement bounded by temperature and margins
        margin = 60.0
        for i in range(n):
            d_len = math.hypot(disp_x[i], disp_y[i])
            if d_len > 0.01:
                step = min(d_len, temp)
                nodes[i].x += (disp_x[i] / d_len) * step
                nodes[i].y += (disp_y[i] / d_len) * step

            nodes[i].x = max(margin, min(width - margin, nodes[i].x))
            nodes[i].y = max(margin, min(height - margin, nodes[i].y))

        temp = max(1.0, temp - cooling)


_OA_BATCH = 50
MAX_EXTERNAL_NODES = 8


def _openalex_key(paper: Paper) -> Optional[str]:
    """OpenAlex work id (W...) embedded in the paper id, if the paper came from OpenAlex."""
    if paper.id.startswith("oa_W"):
        return paper.id[3:]
    return None


def _lookup_doi(paper: Paper) -> Optional[str]:
    if paper.doi:
        return paper.doi.lower()
    if paper.id.startswith("arxiv_"):
        # arXiv registers DataCite DOIs for every preprint.
        arxiv_id = re.sub(r"v\d+$", "", paper.id[len("arxiv_"):])
        return f"10.48550/arxiv.{arxiv_id}".lower()
    return None


def _short_id(openalex_url: str) -> str:
    return openalex_url.rstrip("/").split("/")[-1]


async def _fetch_openalex_records(
    client: OpenAlexClient, filter_field: str, values: list[str], select: str
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for i in range(0, len(values), _OA_BATCH):
        batch = values[i : i + _OA_BATCH]
        try:
            records.extend(
                await client.filter_works(f"{filter_field}:{'|'.join(batch)}", select, per_page=_OA_BATCH)
            )
        except Exception as e:
            logger.warning("OpenAlex batch lookup (%s) failed: %s", filter_field, e)
    return records


async def _resolve_references(
    papers: list[Paper], client: OpenAlexClient
) -> tuple[dict[str, str], dict[str, list[str]]]:
    """Map each paper to its OpenAlex id and the OpenAlex ids it references."""
    paper_to_oa: dict[str, str] = {}
    refs_by_oa: dict[str, list[str]] = {}

    by_oa = {k: p for p in papers if (k := _openalex_key(p))}
    oa_paper_ids = {x.id for x in by_oa.values()}
    by_doi = {
        d: p for p in papers
        if p.id not in oa_paper_ids and (d := _lookup_doi(p)) and "," not in d and "|" not in d
    }

    select = "id,doi,referenced_works"
    oa_records = await _fetch_openalex_records(client, "openalex", list(by_oa), select)
    doi_records = await _fetch_openalex_records(client, "doi", list(by_doi), select)

    for rec in oa_records:
        oa_id = _short_id(rec.get("id", ""))
        paper = by_oa.get(oa_id)
        if paper:
            paper_to_oa[paper.id] = oa_id
            refs_by_oa[oa_id] = [_short_id(r) for r in rec.get("referenced_works") or []]
    for rec in doi_records:
        doi = (rec.get("doi") or "").lower().replace("https://doi.org/", "")
        paper = by_doi.get(doi)
        if paper:
            oa_id = _short_id(rec.get("id", ""))
            paper_to_oa[paper.id] = oa_id
            refs_by_oa[oa_id] = [_short_id(r) for r in rec.get("referenced_works") or []]

    return paper_to_oa, refs_by_oa


def _s2_lookup_id(paper: Paper) -> Optional[str]:
    """Identifier Semantic Scholar's batch endpoint accepts for this paper."""
    if paper.id.startswith("s2_"):
        return paper.id[3:]
    if paper.id.startswith("arxiv_"):
        return "ARXIV:" + re.sub(r"v\d+$", "", paper.id[len("arxiv_"):])
    if paper.doi:
        m = re.match(r"10\.48550/arxiv\.(.+)", paper.doi, re.IGNORECASE)
        return f"ARXIV:{m.group(1)}" if m else f"DOI:{paper.doi}"
    return None


class _Resolution:
    """Citation data from one source: which map papers were found and what they cite."""

    def __init__(self, source: str) -> None:
        self.source = source
        self.paper_key: dict[str, str] = {}          # map paper id -> source work key
        self.refs: dict[str, list[str]] = {}         # source work key -> referenced work keys
        self.external: dict[str, dict[str, Any]] = {}  # work key -> {title, year, citations, first_author}

    @property
    def resolved(self) -> int:
        return len(self.paper_key)


async def _resolve_openalex(papers: list[Paper], client: OpenAlexClient) -> _Resolution:
    res = _Resolution("OpenAlex")
    paper_to_oa, refs_by_oa = await _resolve_references(papers, client)
    res.paper_key = paper_to_oa
    res.refs = refs_by_oa
    return res


async def _openalex_external_meta(client: OpenAlexClient, keys: list[str]) -> dict[str, dict[str, Any]]:
    recs = await _fetch_openalex_records(
        client, "openalex", keys, "id,title,display_name,publication_year,cited_by_count,authorships"
    )
    meta: dict[str, dict[str, Any]] = {}
    for rec in recs:
        key = _short_id(rec.get("id", ""))
        authorships = rec.get("authorships") or []
        first = ((authorships[0].get("author") or {}).get("display_name") or "") if authorships else ""
        meta[key] = {
            "title": rec.get("title") or rec.get("display_name") or "",
            "year": rec.get("publication_year") or 0,
            "citations": rec.get("cited_by_count") or 0,
            "first_author": first,
        }
    return meta


async def _resolve_semantic_scholar(papers: list[Paper], client: SemanticScholarClient) -> _Resolution:
    res = _Resolution("Semantic Scholar")
    lookups = [(p, _s2_lookup_id(p)) for p in papers]
    lookups = [(p, lid) for p, lid in lookups if lid]
    if not lookups:
        return res
    records = await client.batch_references([lid for _, lid in lookups])
    for (paper, _), rec in zip(lookups, records):
        if not rec or not rec.get("paperId"):
            continue
        key = rec["paperId"]
        res.paper_key[paper.id] = key
        refs: list[str] = []
        for ref in rec.get("references") or []:
            rid = ref.get("paperId")
            if not rid:
                continue
            refs.append(rid)
            if rid not in res.external:
                authors = ref.get("authors") or []
                res.external[rid] = {
                    "title": ref.get("title") or "",
                    "year": ref.get("year") or 0,
                    "citations": ref.get("citationCount") or 0,
                    "first_author": (authors[0].get("name") or "") if authors else "",
                }
        res.refs[key] = refs
    return res


def _external_label(meta: dict[str, Any]) -> str:
    year = meta.get("year") or 0
    name = (meta.get("first_author") or "").strip()
    surname = name.split()[-1] if name else ""
    if not surname:
        title = (meta.get("title") or "").strip()
        surname = title[:18] + ("…" if len(title) > 18 else "")
    return f"{surname} '{str(year)[-2:]}" if year else surname


async def build_graph(
    papers: list[Paper] | list[dict[str, Any]],
    openalex_client: Optional[OpenAlexClient] = None,
    s2_client: Optional[SemanticScholarClient] = None,
) -> GraphData:
    """Build the citation graph among the map's papers from real reference lists.

    Edges come from each paper's reference list (OpenAlex `referenced_works`, or
    Semantic Scholar `references` when OpenAlex cannot resolve most papers): an
    edge A -> B means paper A's reference list contains paper B. Works outside the
    map that several map papers cite are added as out-of-map nodes with their real
    metadata. Papers whose references cannot be resolved stay unconnected.
    """
    logger.info("Executing Stage 7: Building citation graph for %d papers", len(papers))

    paper_objs: list[Paper] = []
    for p in papers:
        if isinstance(p, Paper):
            paper_objs.append(p)
        elif isinstance(p, dict):
            paper_objs.append(Paper(**p))

    if not paper_objs:
        return GraphData(nodes=[], edges=[])

    oa = openalex_client or OpenAlexClient()
    s2 = s2_client
    owns_oa = openalex_client is None
    owns_s2 = False
    try:
        res = await _resolve_openalex(paper_objs, oa)
        if res.resolved < len(paper_objs) / 2:
            if s2 is None:
                s2 = SemanticScholarClient()
                owns_s2 = True
            s2_res = await _resolve_semantic_scholar(paper_objs, s2)
            if s2_res.resolved > res.resolved:
                res = s2_res
        logger.info("Citation data source: %s (%d/%d papers resolved)", res.source, res.resolved, len(paper_objs))

        key_to_paper = {key: pid for pid, key in res.paper_key.items()}
        max_citations = max((p.citation_count for p in paper_objs), default=1)

        nodes: list[GraphNode] = []
        for p in paper_objs:
            cit_factor = math.sqrt(max(0, p.citation_count)) / math.sqrt(max(1, max_citations))
            nodes.append(
                GraphNode(
                    paper_id=p.id,
                    size=round(8.0 + 18.0 * cit_factor, 1),
                    year=p.year,
                    in_map=True,
                    label=p.short_label or (p.title[:18] + "…" if len(p.title) > 18 else p.title),
                    citation_count=p.citation_count,
                )
            )

        edges: list[GraphEdge] = []
        edge_set: set[tuple[str, str]] = set()
        external_counts: Counter[str] = Counter()
        for pid, key in res.paper_key.items():
            for ref in set(res.refs.get(key, [])):
                target = key_to_paper.get(ref)
                if target and target != pid:
                    if (pid, target) not in edge_set:
                        edge_set.add((pid, target))
                        edges.append(GraphEdge(source=pid, target=target, weight=1.0))
                elif not target:
                    external_counts[ref] += 1

        # Frequently co-cited works outside the map (real records from the same source).
        min_shared = 2 if len(paper_objs) < 15 else 3
        top_external = [w for w, c in external_counts.most_common(MAX_EXTERNAL_NODES) if c >= min_shared]
        if top_external:
            meta = res.external if res.source != "OpenAlex" else await _openalex_external_meta(oa, top_external)
            prefix = "oa_" if res.source == "OpenAlex" else "s2_"
            ext_max = max([max_citations] + [meta[k]["citations"] for k in top_external if k in meta])
            for key in top_external:
                info = meta.get(key)
                if not info:
                    continue
                node_id = f"{prefix}{key}"
                cites = info["citations"]
                nodes.append(
                    GraphNode(
                        paper_id=node_id,
                        size=round(8.0 + 18.0 * math.sqrt(cites) / math.sqrt(max(1, ext_max)), 1),
                        year=info["year"],
                        in_map=False,
                        label=_external_label(info),
                        citation_count=cites,
                    )
                )
                for pid, src_key in res.paper_key.items():
                    if key in res.refs.get(src_key, []):
                        edges.append(GraphEdge(source=pid, target=node_id, weight=0.6))

        compute_force_directed_layout(nodes, edges, width=1000.0, height=1000.0, iterations=80)
        for node in nodes:
            node.x = round(node.x, 1)
            node.y = round(node.y, 1)

        logger.info("Stage 7 complete: %d nodes, %d citation edges", len(nodes), len(edges))
        return GraphData(nodes=nodes, edges=edges)
    finally:
        if owns_oa:
            await oa.close()
        if owns_s2 and s2 is not None:
            await s2.close()
