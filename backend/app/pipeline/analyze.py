"""Stage 6: Cross-paper analysis — divergent results, research gaps, tools, synthesis.

Everything produced here is derived from the verified extractions of the papers
in the map. Nothing is templated per domain:

- Conflicts are disagreements between papers over verified items: with an LLM,
  it judges which same-quantity measurements and findings really disagree (every
  cited id is checked); without one, a numeric rule compares the same quantity on
  a comparable subject.
- Gaps come from limitations the papers themselves state (verbatim, verified).
  With an LLM configured, related limitations are grouped into gap statements
  that must cite those limitations by id; counts are computed here, not by the LLM.
- The synthesis is either an LLM paragraph restricted to cited, verified
  findings, or a factual corpus summary computed from metadata.
"""

import logging
import re
from collections import Counter
from typing import Any, Optional

from pydantic import BaseModel, Field

from app.clients.llm import LlmClient
from app.models.map import Contradiction, ContradictionEntry, Gap, Synthesis, ToolEntry
from app.models.paper import Finding, Limitation, Metric, Paper
from app.pipeline.metric_names import metric_key
from app.pipeline.units import canonical_unit

logger = logging.getLogger(__name__)

MAX_CONTRADICTIONS = 8
MAX_GAPS = 6
DIVERGENCE_THRESHOLD = 0.25

_STOP = {
    "the", "a", "an", "of", "in", "on", "for", "to", "and", "or", "with", "by", "at", "is", "are",
    "was", "were", "be", "been", "this", "that", "these", "those", "we", "our", "it", "its", "as",
    "from", "not", "can", "may", "which", "such", "also", "has", "have", "had", "than", "their",
    "work", "paper", "study", "approach", "method", "results", "future", "however", "limitation",
    "limitations", "limited", "further", "only", "more", "other", "some", "all", "each", "does",
    "do", "did", "will", "would", "could", "should", "into", "there", "they", "them", "one", "two",
}


def _label(paper: Paper) -> str:
    return paper.short_label or paper.title[:24]


# ──────────────────────────────────────────────────────────
# Disagreements between papers ("conflicts")
# ──────────────────────────────────────────────────────────


# Relative results ("2x faster", "35% lower latency") depend on each paper's own
# baseline, so differing values are not in conflict and are never compared.
_RELATIVE_WORDS = {
    "faster", "slower", "speedup", "speed-up", "larger", "smaller", "higher", "lower", "better",
    "worse", "more", "less", "fewer", "improvement", "reduction", "increase", "decrease", "gain",
    "drop", "efficient", "relative", "enhancement", "change",
}
_RELATIVE_UNITS = {"×", "x", "pp", "times"}
MAX_CONFLICT_ITEMS = 80


def _metric_key(metric: Metric) -> Optional[tuple[str, str]]:
    """Comparison key: canonical quantity + normalized unit. None if not comparable."""
    name = metric_key(metric.name)
    unit = (metric.normalized_unit or canonical_unit(metric.unit) or "").strip()
    if not name or name.endswith(" measurement") or unit in _RELATIVE_UNITS:
        return None
    if any(w in _RELATIVE_WORDS for w in name.split()):
        return None
    return name, unit


def _metric_value(metric: Metric) -> float:
    return metric.normalized_value if metric.normalized_value is not None else metric.value


def _format_conditions(metric: Metric) -> dict[str, str]:
    cond: dict[str, str] = {}
    if metric.conditions.vdd is not None:
        cond["vdd"] = f"{metric.conditions.vdd:g} V"
    if metric.conditions.temp_c is not None:
        cond["temp"] = f"{metric.conditions.temp_c:g} °C"
    if metric.conditions.corner:
        cond["condition"] = metric.conditions.corner
    if metric.conditions.other:
        cond["condition"] = "; ".join(x for x in (cond.get("condition"), metric.conditions.other) if x)
    return cond


def _subject_words(metric: Metric) -> set[str]:
    return {w for w in re.findall(r"[a-z0-9+\-]+", metric.subject.lower()) if w not in _STOP and len(w) > 1}


def _comparable_subjects(a: Metric, b: Metric) -> bool:
    """Same system, as far as the papers say: both unnamed, or mostly the same words."""
    wa, wb = _subject_words(a), _subject_words(b)
    if not wa and not wb:
        return True
    if not wa or not wb:
        return False
    return len(wa & wb) / len(wa | wb) >= 0.5


def _metric_groups(papers: list[Paper]) -> dict[tuple[str, str], list[tuple[Paper, Metric]]]:
    """Every positive, absolute metric grouped by quantity + unit, for groups
    reported by at least two different papers."""
    groups: dict[tuple[str, str], list[tuple[Paper, Metric]]] = {}
    for paper in papers:
        for metric in (paper.extraction.metrics if paper.extraction else []):
            key = _metric_key(metric)
            if key is not None and _metric_value(metric) > 0:
                groups.setdefault(key, []).append((paper, metric))
    return {k: v for k, v in groups.items() if len({p.id for p, _ in v}) >= 2}


def _entry(paper: Paper, metric: Metric) -> ContradictionEntry:
    return ContradictionEntry(
        paper_id=paper.id,
        value=metric.value,
        unit=metric.unit,
        subject=metric.subject,
        conditions=_format_conditions(metric),
        statement=f"{metric.name}: {metric.value:g} {metric.unit}".strip(),
        quote=metric.evidence.quote,
        section=metric.evidence.section,
        page=metric.evidence.page,
    )


def detect_contradictions(papers: list[Paper]) -> list[Contradiction]:
    """Rule-based disagreements (used without an LLM): the same quantity and unit
    reported by different papers more than 25% apart.

    Only comparable reports are compared: the lowest and highest values must be for
    the same stated subject (different materials or variants legitimately differ),
    and at least one side must state its conditions. The explanation lists the
    stated conditions that differ; it never guesses a cause.
    """
    candidates: list[tuple[float, Contradiction]] = []
    for (_name, _unit), members in _metric_groups(papers).items():
        per_paper: dict[str, tuple[Paper, Metric]] = {}
        for paper, metric in members:
            per_paper.setdefault(paper.id, (paper, metric))  # first value each paper reports
        if len(per_paper) < 2:
            continue
        entries = sorted(per_paper.values(), key=lambda pm: _metric_value(pm[1]))
        (p_lo, m_lo), (p_hi, m_hi) = entries[0], entries[-1]
        lo, hi = _metric_value(m_lo), _metric_value(m_hi)
        spread = (hi - lo) / hi if hi else 0.0
        if spread <= DIVERGENCE_THRESHOLD or not _comparable_subjects(m_lo, m_hi):
            continue
        c_lo, c_hi = _format_conditions(m_lo), _format_conditions(m_hi)
        if not c_lo and not c_hi:
            # Neither paper states the conditions behind its number, so a
            # difference says nothing about agreement.
            continue

        differing = [
            f"{label}: {c_lo.get(k) or 'not stated'} vs {c_hi.get(k) or 'not stated'}"
            for k, label in (("vdd", "supply voltage"), ("temp", "temperature"), ("condition", "test condition"))
            if c_lo.get(k) != c_hi.get(k)
        ]
        t_lo = p_lo.extraction.technology if p_lo.extraction else None
        t_hi = p_hi.extraction.technology if p_hi.extraction else None
        if t_lo and t_hi and t_lo.node_nm and t_hi.node_nm and t_lo.node_nm != t_hi.node_nm:
            differing.append(f"process node: {t_lo.node_nm} nm vs {t_hi.node_nm} nm")
        if t_lo and t_hi and t_lo.device and t_hi.device and t_lo.device.lower() != t_hi.device.lower():
            differing.append(f"platform: {t_lo.device} vs {t_hi.device}")

        head = (
            f"{_label(p_lo)} reports {m_lo.value:g} {m_lo.unit} and {_label(p_hi)} reports "
            f"{m_hi.value:g} {m_hi.unit} for {m_hi.name} ({int(round(spread * 100))}% apart)."
        )
        if differing:
            reason = f"{head} Reported conditions differ — {'; '.join(differing)}."
        else:
            shared = ", ".join(c_lo.values())
            reason = f"{head} Both values are reported under the same stated conditions ({shared})."

        both_full = p_lo.has_full_text and p_hi.has_full_text
        candidates.append((len(per_paper) + spread, Contradiction(
            id="",
            kind="value",
            metric=m_hi.name,
            entries=[_entry(p, m) for p, m in entries],
            likely_reason=reason,
            confidence="HIGH" if both_full else "MED",
        )))

    candidates.sort(key=lambda c: c[0], reverse=True)
    result = [c for _, c in candidates[:MAX_CONTRADICTIONS]]
    for i, c in enumerate(result, start=1):
        c.id = f"c_{i}"
    return result


class _LlmConflict(BaseModel):
    topic: str = Field(description="What the disagreement is about, a few words (e.g. 'LLZO ionic conductivity at room temperature')")
    item_ids: list[str] = Field(description="IDs (M# or F#) of the items that disagree, from at least two different papers")
    explanation: str = Field(description="One or two sentences: what each side reports and any stated difference in material or conditions that could explain it. Only facts from the listed items.")


class _LlmConflictResponse(BaseModel):
    conflicts: list[_LlmConflict] = Field(default_factory=list)


async def llm_conflicts(papers: list[Paper], topic: str, llm: LlmClient) -> list[Contradiction]:
    """Disagreements judged by the LLM over verified items only; every cited id is checked.

    Candidates are measurements of the same quantity reported by different papers,
    plus the papers' verified findings. The LLM decides which really disagree (same
    or equivalent system and comparable conditions, or opposing claims about the
    same effect); results that differ because the materials or conditions differ
    are not disagreements.
    """
    items: dict[str, tuple[Paper, Any]] = {}
    lines: list[str] = []
    for (_name, _unit), members in sorted(_metric_groups(papers).items(), key=lambda kv: -len(kv[1])):
        for paper, metric in members:
            if len(items) >= MAX_CONFLICT_ITEMS // 2:
                break
            mid = f"M{sum(1 for k in items if k.startswith('M')) + 1}"
            items[mid] = (paper, metric)
            cond = "; ".join(_format_conditions(metric).values())
            lines.append(
                f"{mid} [{_label(paper)}] {metric.name} = {metric.value:g} {metric.unit}"
                + (f" | subject: {metric.subject}" if metric.subject else "")
                + (f" | conditions: {cond}" if cond else "")
            )
    for paper in papers:
        for finding in (paper.extraction.findings if paper.extraction else [])[:3]:
            if len(items) >= MAX_CONFLICT_ITEMS:
                break
            fid = f"F{sum(1 for k in items if k.startswith('F')) + 1}"
            items[fid] = (paper, finding)
            lines.append(f"{fid} [{_label(paper)}] {finding.text}")
    if len({p.id for p, _ in items.values()}) < 2:
        return []

    prompt = f"""Research topic: {topic}

Verified results from different papers. M# are measurements, F# are findings.

{chr(10).join(lines)}

Identify genuine disagreements between different papers, at most {MAX_CONTRADICTIONS}:
- the same quantity measured on the same or an equivalent system under comparable conditions, with
  materially different values; or
- findings that make opposing claims about the same effect.
Do NOT report values that differ because the materials, samples, variants or test conditions differ,
and do not report results that are merely different. Each disagreement must cite items from at least
two different papers. Return an empty list when there are none; that is a normal answer.
"""
    try:
        resp = await llm.structured(prompt, _LlmConflictResponse)
    except Exception as e:
        logger.warning("LLM conflict check failed, using the numeric rule: %s", e)
        return detect_contradictions(papers)

    result: list[Contradiction] = []
    for c in resp.conflicts:
        cited = []
        for raw in c.item_ids:
            key = raw.strip().strip("[]").upper()
            if key in items and key not in cited:
                cited.append(key)
        cited_papers = {items[k][0].id for k in cited}
        if len(cited_papers) < 2 or not c.explanation.strip():
            continue
        entries = []
        for key in cited:
            paper, item = items[key]
            if isinstance(item, Metric):
                entries.append(_entry(paper, item))
            else:
                entries.append(ContradictionEntry(
                    paper_id=paper.id, statement=item.text, quote=item.evidence.quote,
                    section=item.evidence.section, page=item.evidence.page,
                ))
        kind = "value" if all(k.startswith("M") for k in cited) else "claim"
        metric_name = next((items[k][1].name for k in cited if k.startswith("M")), "")
        all_full = all(items[k][0].has_full_text for k in cited)
        result.append(Contradiction(
            id=f"c_{len(result) + 1}",
            kind=kind,
            metric=c.topic.strip() or metric_name,
            entries=entries,
            likely_reason=c.explanation.strip(),
            confidence="MED" if all_full else "LOW",
        ))
        if len(result) >= MAX_CONTRADICTIONS:
            break
    return result


# ──────────────────────────────────────────────────────────
# Research gaps
# ──────────────────────────────────────────────────────────


def _content_words(text: str) -> set[str]:
    words = re.findall(r"[a-z][a-z\-]{2,}", text.lower())
    return {w.rstrip("s") for w in words if w not in _STOP}


def _collect_limitations(papers: list[Paper]) -> list[tuple[str, Paper, Limitation]]:
    items: list[tuple[str, Paper, Limitation]] = []
    for paper in papers:
        if not paper.extraction:
            continue
        for lim in paper.extraction.limitations:
            items.append((f"L{len(items) + 1}", paper, lim))
    return items


def _pattern(k: int, n: int) -> str:
    noun = "paper" if n == 1 else "papers"
    return f"{k} of {n} {noun} in this map explicitly report this limitation"


def heuristic_gaps(papers: list[Paper]) -> list[Gap]:
    """Group stated limitations that share vocabulary across papers.

    Each gap statement is a verbatim limitation sentence; the pattern counts the
    papers whose own limitation statements fall in the same group.
    """
    items = _collect_limitations(papers)
    if not items:
        return []

    total = len(papers)
    word_sets = [_content_words(lim.text) for _, _, lim in items]
    clusters: list[list[int]] = []
    for i, ws in enumerate(word_sets):
        placed = False
        for cluster in clusters:
            rep = word_sets[cluster[0]]
            overlap = len(ws & rep) / max(1, min(len(ws), len(rep)))
            if overlap >= 0.4 and len(ws & rep) >= 2:
                cluster.append(i)
                placed = True
                break
        if not placed:
            clusters.append([i])

    def cluster_papers(cluster: list[int]) -> list[str]:
        seen: list[str] = []
        for idx in cluster:
            pid = items[idx][1].id
            if pid not in seen:
                seen.append(pid)
        return seen

    clusters.sort(key=lambda c: (len(cluster_papers(c)), len(c)), reverse=True)

    gaps: list[Gap] = []
    for cluster in clusters[:MAX_GAPS]:
        paper_ids = cluster_papers(cluster)
        _, paper, lim = items[cluster[0]]
        gaps.append(
            Gap(
                id=f"gap_{len(gaps) + 1}",
                statement=lim.text,
                pattern=_pattern(len(paper_ids), total),
                supporting_paper_ids=paper_ids,
                why_it_matters=(
                    f"Stated by the authors of {', '.join(_label(p) for p in papers if p.id in paper_ids)} "
                    "as a limitation of their own work."
                ),
                confidence="MED" if len(paper_ids) > 1 else "LOW",
            )
        )
    return gaps


class _LlmGap(BaseModel):
    statement: str = Field(description="One-sentence research gap, specific to this literature")
    why_it_matters: str = Field(description="One or two sentences on why closing this gap matters")
    limitation_ids: list[str] = Field(description="IDs (e.g. L3, L7) of the stated limitations that support this gap")


class _LlmGapResponse(BaseModel):
    gaps: list[_LlmGap] = Field(default_factory=list)


async def llm_gaps(papers: list[Paper], topic: str, llm: LlmClient) -> list[Gap]:
    """Ask the LLM to group verified limitations into gaps, then validate every citation."""
    items = _collect_limitations(papers)
    if not items:
        return []

    listing = "\n".join(
        f"{lid} [{_label(paper)}]: {lim.text}" for lid, paper, lim in items
    )
    prompt = f"""Research topic: {topic}

Below are limitations that the authors of {len(papers)} papers state about their own work.
Each line is: <limitation id> [<paper>]: <limitation>.

{listing}

Group these into at most {MAX_GAPS} research gaps for this topic. Rules:
- Every gap must be supported by one or more of the limitation ids above.
- Prefer gaps supported by several papers.
- Do not introduce facts, numbers, or claims that are not in the listed limitations.
"""
    try:
        resp = await llm.structured(prompt, _LlmGapResponse)
    except Exception as e:
        logger.warning("LLM gap synthesis failed, using stated limitations directly: %s", e)
        return heuristic_gaps(papers)

    by_id = {lid: (paper, lim) for lid, paper, lim in items}
    total = len(papers)
    gaps: list[Gap] = []
    for g in resp.gaps:
        cited = [lid.strip().upper() for lid in g.limitation_ids if lid.strip().upper() in by_id]
        if not cited or not g.statement.strip():
            continue
        paper_ids: list[str] = []
        for lid in cited:
            pid = by_id[lid][0].id
            if pid not in paper_ids:
                paper_ids.append(pid)
        gaps.append(
            Gap(
                id=f"gap_{len(gaps) + 1}",
                statement=g.statement.strip(),
                pattern=_pattern(len(paper_ids), total),
                supporting_paper_ids=paper_ids,
                why_it_matters=g.why_it_matters.strip(),
                confidence="HIGH" if len(paper_ids) > 1 else "MED",
            )
        )
        if len(gaps) >= MAX_GAPS:
            break

    return gaps or heuristic_gaps(papers)


# ──────────────────────────────────────────────────────────
# Tools, findings, synthesis
# ──────────────────────────────────────────────────────────


def aggregate_tools_and_datasets(papers: list[Paper]) -> list[ToolEntry]:
    """Aggregate referenced tools and datasets across papers (case-insensitive names)."""
    tool_map: dict[str, tuple[str, str, set[str]]] = {}

    def add(name: str, category: str, paper_id: str) -> None:
        name = " ".join(name.split())
        if not name:
            return
        key = name.lower()
        display, cat, ids = tool_map.setdefault(key, (name, category, set()))
        ids.add(paper_id)
        if not cat and category:
            tool_map[key] = (display, category, ids)

    for paper in papers:
        if not paper.extraction:
            continue
        for t in paper.extraction.tools:
            add(t.name, t.category or "tool", paper.id)
        for d in paper.extraction.datasets:
            add(d.name, d.category or "dataset", paper.id)

    entries = [
        ToolEntry(name=display, category=cat, count=len(ids), paper_ids=sorted(ids))
        for display, cat, ids in tool_map.values()
    ]
    entries.sort(key=lambda t: (t.count, t.name.lower()), reverse=True)
    return entries


def collect_verified_findings(papers: list[Paper]) -> list[Finding]:
    """Aggregate verified findings across papers."""
    findings: list[Finding] = []
    for paper in papers:
        if paper.extraction:
            findings.extend(paper.extraction.findings)
    return findings


def corpus_synthesis(papers: list[Paper], topic: str) -> Synthesis:
    """Factual summary of the corpus computed from metadata only."""
    if not papers:
        return Synthesis()
    years = [p.year for p in papers if p.year > 0]
    full = sum(1 for p in papers if p.has_full_text)
    top = sorted(papers, key=lambda p: p.citation_count, reverse=True)[:3]
    venues = Counter("arXiv" if "arxiv" in p.venue.lower() else p.venue for p in papers if p.venue)
    parts = [
        f"This map covers {len(papers)} papers on “{topic}”"
        + (f" published {min(years)}–{max(years)}" if years else "")
        + f"; {full} were analysed from full text and {len(papers) - full} from abstracts only."
    ]
    if top:
        parts.append(
            "Most cited: "
            + "; ".join(f"{_label(p)} ({p.citation_count} citations)" for p in top)
            + "."
        )
    common_venues = [v for v, c in venues.most_common(3) if c > 1]
    if common_venues:
        parts.append("Recurring venues: " + ", ".join(common_venues) + ".")
    return Synthesis(text=" ".join(parts), citation_ids=[p.id for p in top])


class _LlmSynthesis(BaseModel):
    text: str = Field(description="One paragraph (4-6 sentences) synthesizing the findings, citing [F#] ids inline")
    finding_ids: list[str] = Field(description="All F# ids cited in the paragraph")


async def llm_synthesis(papers: list[Paper], topic: str, llm: LlmClient) -> Optional[Synthesis]:
    """LLM overview grounded only in verified findings; citations are validated."""
    listing: list[str] = []
    index: dict[str, str] = {}
    for paper in papers:
        if not paper.extraction:
            continue
        for f in paper.extraction.findings[:3]:
            fid = f"F{len(index) + 1}"
            index[fid] = paper.id
            listing.append(f"{fid} [{_label(paper)}, {paper.year}]: {f.text}")
    if len(listing) < 3:
        return None

    prompt = f"""Research topic: {topic}

Verified findings from the papers in this research map:
{chr(10).join(listing[:60])}

Write one paragraph that synthesizes what this literature shows about the topic: the main
approaches, the strongest results, and where the papers agree or differ. Cite findings inline as
[F#]. Use only the findings above; do not add outside facts or numbers.
"""
    try:
        resp = await llm.structured(prompt, _LlmSynthesis)
    except Exception as e:
        logger.warning("LLM synthesis failed: %s", e)
        return None

    # Models cite as [F3], (F3), (F1, F2), [F1; F4] or ranges like (F2-F5) with any
    # dash. Resolve every id against the findings list; unknown ids are dropped,
    # never shown.
    dash = "\u2010\u2011\u2012\u2013\u2014-"  # hyphen last: literal inside [...]
    sep = rf"\s*(?:[,;{dash}]|and|to)\s*"
    group_re = re.compile(rf"\s*[\(\[]\s*(F\d+(?:{sep}F?\d+)*)\s*[\)\]]")

    def expand(group: str) -> list[str]:
        ids: list[str] = []
        tokens = re.findall(rf"F?(\d+)|([{dash}]|\bto\b)", group)
        pending_range = False
        for num, rng in tokens:
            if rng:
                pending_range = bool(ids)
                continue
            n = int(num)
            if pending_range:
                start = int(ids[-1][1:])
                ids.extend(f"F{k}" for k in range(start + 1, min(n, start + 10) + 1))
                pending_range = False
            else:
                ids.append(f"F{n}")
        return ids

    cited = [fid for m in group_re.finditer(resp.text) for fid in expand(m.group(1))]
    cited += [f.strip().upper() for f in resp.finding_ids]
    valid = [fid for fid in cited if fid in index]
    if not valid:
        return None
    paper_ids: list[str] = []
    for fid in valid:
        if index[fid] not in paper_ids:
            paper_ids.append(index[fid])

    labels = {p.id: _label(p) for p in papers}

    def replace_group(m: re.Match) -> str:
        seen: list[str] = []
        for fid in expand(m.group(1)):
            if fid in index:
                label = labels[index[fid]]
                if label not in seen:
                    seen.append(label)
        return f" [{'; '.join(seen)}]" if seen else ""

    text = group_re.sub(replace_group, resp.text)
    text = re.sub(r"\bF\d+\b", "", text)  # stray bare ids
    text = re.sub(rf"\s*[\(\[][\s,;{dash}]*[\)\]]", "", text)  # brackets emptied by the above
    text = re.sub(r"\s+([.,;:])", r"\1", re.sub(r"[ \t]{2,}", " ", text)).strip()
    text = re.sub(r"(\[[^\]]+\])(\s*\1)+", r"\1", text)  # "[A] [A]" -> "[A]"
    return Synthesis(text=text, citation_ids=paper_ids)


async def analyze(papers: list[Paper], topic: str, llm: Optional[LlmClient] = None) -> dict[str, Any]:
    """Cross-paper analysis: divergent results, gaps, tools, findings, synthesis."""
    logger.info("Executing Stage 6: Cross-paper analysis for %d papers", len(papers))

    use_llm = llm is not None and llm.is_configured()

    contradictions = await llm_conflicts(papers, topic, llm) if use_llm else detect_contradictions(papers)
    gaps = await llm_gaps(papers, topic, llm) if use_llm else heuristic_gaps(papers)
    tools = aggregate_tools_and_datasets(papers)
    findings = collect_verified_findings(papers)

    synthesis: Optional[Synthesis] = None
    if use_llm:
        synthesis = await llm_synthesis(papers, topic, llm)
    if synthesis is None:
        synthesis = corpus_synthesis(papers, topic)

    logger.info(
        "Stage 6 complete: %d contradictions, %d gaps, %d tool entries, %d findings",
        len(contradictions), len(gaps), len(tools), len(findings),
    )

    return {
        "contradictions": contradictions,
        "gaps": gaps,
        "tools": tools,
        "findings": findings,
        "synthesis": synthesis,
    }
