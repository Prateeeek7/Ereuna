"""Stage 1: Query expansion.

Turns a user research topic (e.g., "Low-leakage SRAM using FinFET") into 4–6 targeted
search queries and domain synonyms to maximize recall across academic databases.
"""

import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

from app.clients.llm import LlmClient

logger = logging.getLogger(__name__)


class ExpandedQueries(BaseModel):
    """Schema for LLM structured query expansion."""

    queries: list[str] = Field(
        ...,
        description="4 to 6 distinct academic search keyword phrases focusing on methods, terminology, and synonyms.",
    )


# Meaning-preserving acronym expansions only. Used to add a spelled-out variant
# of the user's topic so databases that index full terms still match.
DOMAIN_EXPANSIONS: dict[str, list[str]] = {
    "sram": ["static random access memory"],
    "dram": ["dynamic random access memory"],
    "finfet": ["fin field-effect transistor"],
    "mosfet": ["metal-oxide-semiconductor field-effect transistor"],
    "mof": ["metal-organic framework"],
    "mofs": ["metal-organic frameworks"],
    "dac": ["direct air capture"],
    "gnn": ["graph neural network"],
    "gnns": ["graph neural networks"],
    "llm": ["large language model"],
    "llms": ["large language models"],
    "moe": ["mixture of experts"],
    "rl": ["reinforcement learning"],
    "nlp": ["natural language processing"],
    "cnn": ["convolutional neural network"],
    "stdp": ["spike-timing dependent plasticity"],
    "snn": ["spiking neural network"],
    "crispr": ["clustered regularly interspaced short palindromic repeats"],
    "kv": ["key-value"],
}


_STOPWORDS = {
    "a", "an", "the", "of", "for", "in", "on", "using", "with", "via", "and", "or",
    "to", "by", "from", "towards", "toward", "based", "through", "into", "at",
}

# Words that usually join "what" to "how/where" in a research topic.
_CLAUSE_SPLIT_RE = re.compile(r"\s+(?:for|using|in|with|via|on|under|through|based on)\s+", re.IGNORECASE)


def heuristic_expand_query(topic: str) -> list[str]:
    """Generate search query variants deterministically from the topic text itself.

    Used when no LLM is configured. Every variant is derived from the user's own
    words (acronym/synonym swaps, keyword-only form, and the topic's clauses), so
    no topic-specific query is ever injected.
    """
    clean_topic = " ".join(topic.strip().split())
    words = re.findall(r"[\w-]+", clean_topic.lower())

    queries: list[str] = [clean_topic]

    # Variation 1: acronym / terminology swap
    v1_words = list(words)
    applied = 0
    for i, word in enumerate(words):
        if word in DOMAIN_EXPANSIONS and applied < 2:
            v1_words[i] = DOMAIN_EXPANSIONS[word][0]
            applied += 1
    if applied:
        queries.append(" ".join(v1_words))

    # Variation 2: keyword-only form (drops connective words)
    keywords = [w for w in words if w not in _STOPWORDS]
    if keywords and len(keywords) != len(words):
        queries.append(" ".join(keywords))

    # Variation 3: the topic's main subject clause on its own (text before
    # "for/using/in/..."), which broadens recall without changing the subject.
    clauses = [c.strip() for c in _CLAUSE_SPLIT_RE.split(clean_topic) if c.strip()]
    if len(clauses) > 1 and len(re.findall(r"[\w-]+", clauses[0])) >= 2:
        queries.append(clauses[0])

    # Deduplicate while preserving order and limit to 6
    unique_queries: list[str] = []
    seen: set[str] = set()
    for q in queries:
        normalized = " ".join(q.lower().split())
        if normalized and normalized not in seen:
            seen.add(normalized)
            unique_queries.append(q)

    return unique_queries[:6]


async def expand_query(topic: str, llm_client: Optional[LlmClient] = None) -> list[str]:
    """Turn a research topic into 4–6 keyword queries and synonyms.

    Args:
        topic: User input topic string (e.g., 'Low-leakage SRAM using FinFET').
        llm_client: Optional LLM client for structured prompt expansion.

    Returns:
        List of 4 to 6 search query strings.
    """
    if llm_client and llm_client.is_configured():
        prompt = (
            f"You are an expert scientific researcher. Given the research topic:\n"
            f"'{topic}'\n\n"
            f"Generate 4 to 6 specific, keyword-dense search query phrases suitable for academic "
            f"search engines (OpenAlex, Semantic Scholar, arXiv).\n"
            f"Cover specific terminology, device nodes or materials, circuit or algorithmic "
            f"mechanisms, and industry-standard synonyms."
        )
        try:
            result = await llm_client.structured(prompt, ExpandedQueries)
            llm_queries = [q.strip().strip("\"'“”‘’").strip() for q in result.queries if q and q.strip()]
            llm_queries = [q for q in llm_queries if q]
            if len(llm_queries) >= 3:
                # Always keep the user's exact topic as the first query.
                merged = [topic.strip()] + [
                    q for q in llm_queries if q.lower() != topic.strip().lower()
                ]
                return merged[:6]
        except Exception as e:
            logger.warning("LLM query expansion failed, falling back to heuristics: %s", e)

    return heuristic_expand_query(topic)
