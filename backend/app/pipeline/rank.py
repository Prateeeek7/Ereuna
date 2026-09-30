"""Stage 3: Multi-factor ranking, filtering, and relevance reason generation.

Computes a composite relevance score based on:
  Score = 0.65 * Similarity + 0.15 * log(Citations) + 0.10 * Recency + 0.10 * VenueQuality
Filters by MapFilters and attaches a plain-language relevance_reason.
"""

import datetime
import logging
import math
import re
from typing import Optional

from app.models.map import MapFilters
from app.models.paper import Paper

logger = logging.getLogger(__name__)

# Global cache for sentence-transformers model to avoid reloading on every request
_EMBEDDING_MODEL = None
_EMBEDDING_ATTEMPTED = False

TOP_TIER_VENUES = {
    "ieee", "acm", "nature", "science", "jssc", "ted", "tvlsi", "tcsi",
    "iedm", "isscc", "islped", "dac", "date", "vlsit", "neurips", "icml",
    "iclr", "cvpr", "acl", "emnlp", "kdd", "cell", "pnas",
}

MID_TIER_VENUES = {
    "access", "microelectronics", "integration", "springer", "elsevier",
    "wiley", "applied physics", "sensors", "electronics",
}


def _get_embedding_model():
    """Lazily load SentenceTransformer model if available."""
    global _EMBEDDING_MODEL, _EMBEDDING_ATTEMPTED
    if _EMBEDDING_MODEL is not None:
        return _EMBEDDING_MODEL
    if _EMBEDDING_ATTEMPTED:
        return None

    _EMBEDDING_ATTEMPTED = True
    try:
        from sentence_transformers import SentenceTransformer

        logger.info("Loading sentence-transformers embedding model (all-MiniLM-L6-v2)...")
        _EMBEDDING_MODEL = SentenceTransformer("all-MiniLM-L6-v2")
        return _EMBEDDING_MODEL
    except Exception as e:
        logger.debug("SentenceTransformer not available (%s); using lexical scoring fallback", e)
        return None


def lexical_similarity(query: str, text: str) -> float:
    """Fallback lexical keyword & phrase overlap score between query and text [0.0 - 1.0]."""
    if not query or not text:
        return 0.0

    q_words = set(re.findall(r"\w+", query.lower()))
    if not q_words:
        return 0.0

    t_words = set(re.findall(r"\w+", text.lower()))

    # Direct keyword overlap
    overlap = len(q_words & t_words) / len(q_words)

    # Substring phrase boost
    clean_q = " ".join(query.lower().split())
    clean_t = " ".join(text.lower().split())
    phrase_boost = 0.3 if clean_q in clean_t else 0.0

    score = min(1.0, overlap * 0.7 + phrase_boost)
    return max(0.0, score)


def compute_similarity_scores(topic: str, papers: list[Paper]) -> list[float]:
    """Compute semantic similarity scores [0.0 - 1.0] for all papers."""
    model = _get_embedding_model()

    if model is not None:
        try:
            texts = [f"{p.title}. {p.abstract}" for p in papers]
            topic_emb = model.encode(topic, convert_to_tensor=True)
            paper_embs = model.encode(texts, convert_to_tensor=True)

            from sentence_transformers import util

            cos_sims = util.cos_sim(topic_emb, paper_embs)[0].tolist()
            # Negative cosine means unrelated; clip to [0, 1].
            return [max(0.0, min(1.0, s)) for s in cos_sims]
        except Exception as e:
            logger.warning("Embedding similarity failed, falling back to lexical: %s", e)

    # Lexical fallback
    return [lexical_similarity(topic, f"{p.title} {p.abstract}") for p in papers]


UNREADABLE_PENALTY = 0.8


def score_venue(venue: str) -> float:
    """Score venue prestige [0.0 - 1.0]."""
    if not venue:
        return 0.3

    lower = venue.lower()
    for top in TOP_TIER_VENUES:
        if top in lower:
            return 1.0

    for mid in MID_TIER_VENUES:
        if mid in lower:
            return 0.7

    if "arxiv" in lower:
        return 0.5

    return 0.4


def score_recency(year: int, current_year: Optional[int] = None) -> float:
    """Score publication recency [0.0 - 1.0] with linear decay over 10 years."""
    if year <= 1900:
        return 0.3

    curr = current_year or datetime.datetime.now().year
    diff = max(0, curr - year)
    return max(0.0, 1.0 - (diff * 0.08))


def generate_relevance_reason(
    paper: Paper,
    sim_score: float,
    cit_score: float,
    venue_score: float,
) -> str:
    """Concise, factual relevance reason built only from the computed scores and metadata."""
    reasons = []

    if sim_score >= 0.6:
        reasons.append(f"Strong topical match to the query (similarity {sim_score:.2f})")
    elif sim_score >= 0.4:
        reasons.append(f"Good topical match to the query (similarity {sim_score:.2f})")
    else:
        reasons.append(f"Partial topical match to the query (similarity {sim_score:.2f})")

    if paper.citation_count > 0:
        reasons.append(f"{paper.citation_count} citations")

    if venue_score >= 0.9 and paper.venue:
        reasons.append(f"published in {paper.venue}")
    elif paper.year >= datetime.datetime.now().year - 2:
        reasons.append(f"recent ({paper.year})")

    if paper.oa_pdf_url:
        reasons.append("open-access PDF available")
    elif not (paper.abstract or "").strip():
        reasons.append("no abstract or open PDF, so only its metadata is used")

    return "; ".join(reasons) + "."


def rank_and_filter_papers(
    topic: str,
    papers: list[Paper],
    filters: Optional[MapFilters] = None,
    max_papers: int = 25,
) -> list[Paper]:
    """Score, filter, and rank candidate papers.

    Args:
        topic: User research topic.
        papers: Deduplicated candidates from Stage 2.
        filters: User-applied criteria (year, min citations, OA only).
        max_papers: Maximum papers to keep (default 25).

    Returns:
        Top N scored Paper objects with attached relevance_reason.
    """
    if not papers:
        return []

    filters = filters or MapFilters()

    # Step 1: Apply hard filter exclusions
    filtered: list[Paper] = []
    for p in papers:
        if filters.year_min and p.year > 0 and p.year < filters.year_min:
            continue
        if filters.year_max and p.year > 0 and p.year > filters.year_max:
            continue
        if p.citation_count < filters.min_citations:
            continue
        if filters.open_access_only and not p.has_full_text:
            continue
        filtered.append(p)

    if not filtered:
        # Fall back to unfiltered if filters eliminated everything
        filtered = papers

    # Step 2: Calculate component scores
    sim_scores = compute_similarity_scores(topic, filtered)

    # Drop clearly off-topic candidates (well below the best match) as long as
    # enough papers remain to fill the map.
    limit = filters.max_papers or max_papers
    if sim_scores:
        best = max(sim_scores)
        floor = best * 0.55
        keep = [i for i, s in enumerate(sim_scores) if s >= floor]
        if len(keep) >= min(limit, len(filtered)) // 2 and len(keep) < len(filtered):
            filtered = [filtered[i] for i in keep]
            sim_scores = [sim_scores[i] for i in keep]
    max_cits = max([p.citation_count for p in filtered] + [100])
    curr_year = datetime.datetime.now().year

    for i, p in enumerate(filtered):
        s_sim = sim_scores[i]

        # Log citations normalized
        s_cit = math.log1p(p.citation_count) / math.log1p(max_cits)

        # Recency score
        s_rec = score_recency(p.year, curr_year)

        # Venue score
        s_ven = score_venue(p.venue)

        # Multi-factor score: topical similarity dominates so highly cited but
        # off-topic surveys do not crowd out on-topic work.
        composite = (0.65 * s_sim) + (0.15 * s_cit) + (0.10 * s_rec) + (0.10 * s_ven)
        # With no abstract and no open PDF the similarity rests on the title alone
        # (short titles score high) and nothing can be extracted from the paper,
        # so it yields its place to an equally relevant paper we can read.
        if not (p.abstract or "").strip() and not p.oa_pdf_url:
            composite *= UNREADABLE_PENALTY
        p.relevance_score = round(composite, 3)

        # Plain language justification
        p.relevance_reason = generate_relevance_reason(p, s_sim, s_cit, s_ven)

    # Step 3: Sort by composite score descending
    filtered.sort(key=lambda x: x.relevance_score, reverse=True)

    # Step 4: Keep top N
    return filtered[:limit]

