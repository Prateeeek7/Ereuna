"""Phase 2 evaluation script: Test evidence verification across 5 benchmark topics.

Test topics:
1. Low-leakage SRAM using FinFET
2. Transformer models for protein structure prediction
3. Perovskite solar cell stability
4. Federated learning in healthcare privacy
5. Graph neural networks for traffic forecasting
"""

import asyncio
import json
import time

from app.models.map import MapFilters
from app.pipeline.expand_query import expand_query
from app.pipeline.extract import extract
from app.pipeline.fetch_fulltext import fetch_fulltext
from app.pipeline.rank import rank_and_filter_papers
from app.pipeline.retrieve import retrieve_candidates

TOPICS = [
    "Low-leakage SRAM using FinFET",
    "Transformer models for protein structure prediction",
    "Perovskite solar cell stability",
    "Federated learning in healthcare privacy",
    "Graph neural networks for traffic forecasting",
]


async def evaluate_topic(topic: str, max_papers: int = 10) -> dict:
    print(f"\n{'='*75}")
    print(f"Evaluating: {topic}")
    print(f"{'='*75}")

    t0 = time.time()
    # 1. Expand query
    queries = await expand_query(topic)
    print(f"  [1/5] Expanded into {len(queries)} queries")

    # 2. Retrieve
    candidates = await retrieve_candidates(queries)
    print(f"  [2/5] Retrieved {len(candidates)} candidates")

    # 3. Rank
    filters = MapFilters(max_papers=max_papers)
    ranked = rank_and_filter_papers(topic, candidates, filters=filters, max_papers=max_papers)
    print(f"  [3/5] Ranked top {len(ranked)} papers")

    # 4. Fetch full text
    papers_with_text = await fetch_fulltext(ranked)
    full_text_count = sum(1 for p in papers_with_text if p.has_full_text)
    abstract_count = len(papers_with_text) - full_text_count
    print(f"  [4/5] Full text: {full_text_count}, Abstract-only: {abstract_count}")

    # 5. Extract & verify evidence
    extracted_papers, reports = await extract(papers_with_text)
    total_items = sum(r.total_items for r in reports)
    verified_items = sum(r.verified_items for r in reports)
    dropped_items = sum(r.dropped_items for r in reports)
    pass_rate = (verified_items / total_items * 100) if total_items > 0 else 0.0

    elapsed = time.time() - t0
    print(f"  [5/5] Extracted {total_items} items: {verified_items} verified, {dropped_items} dropped ({pass_rate:.1f}% pass rate) in {elapsed:.1f}s")

    return {
        "topic": topic,
        "papers_selected": len(ranked),
        "full_text_count": full_text_count,
        "abstract_count": abstract_count,
        "total_items": total_items,
        "verified_items": verified_items,
        "dropped_items": dropped_items,
        "pass_rate": round(pass_rate, 1),
        "elapsed_seconds": round(elapsed, 1),
    }


async def main():
    print("=" * 75)
    print(" ResearchRadar — Phase 2 Evidence Verification Benchmark (5 Topics)")
    print("=" * 75)

    results = []
    for topic in TOPICS:
        res = await evaluate_topic(topic, max_papers=10)
        results.append(res)

    print("\n" + "=" * 75)
    print(f"{'Topic':<45} {'Papers':<7} {'FT':<4} {'Items':<7} {'Pass %':<8}")
    print("-" * 75)

    tot_items = 0
    tot_verified = 0
    tot_dropped = 0
    for r in results:
        tot_items += r["total_items"]
        tot_verified += r["verified_items"]
        tot_dropped += r["dropped_items"]
        t_trunc = r["topic"] if len(r["topic"]) <= 44 else r["topic"][:41] + "..."
        print(f"{t_trunc:<45} {r['papers_selected']:<7} {r['full_text_count']:<4} {r['total_items']:<7} {r['pass_rate']}%")

    overall_pass_rate = (tot_verified / tot_items * 100) if tot_items > 0 else 0.0
    print("-" * 75)
    print(f"OVERALL: {tot_verified}/{tot_items} items verified across 5 topics ({overall_pass_rate:.1f}% kept)")
    print("=" * 75)

    # Save results as JSON
    with open("scripts/benchmark_results.json", "w") as f:
        json.dump(
            {
                "overall_pass_rate": round(overall_pass_rate, 1),
                "total_items": tot_items,
                "verified_items": tot_verified,
                "dropped_items": tot_dropped,
                "topics": results,
            },
            f,
            indent=2,
        )


if __name__ == "__main__":
    asyncio.run(main())
