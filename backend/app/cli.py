"""Command-line interface for Ereuna.

Usage:
    python -m app.cli map "Low-leakage SRAM using FinFET"
    python -m app.cli map "Solid-state battery electrolyte degradation" --max-papers 20
    python -m app.cli map "Transformer models for protein structure prediction" --extract
"""

import argparse
import asyncio
import sys
import time

from app.models.map import MapFilters
from app.pipeline.expand_query import expand_query
from app.pipeline.extract import extract
from app.pipeline.fetch_fulltext import fetch_fulltext
from app.pipeline.rank import rank_and_filter_papers
from app.pipeline.retrieve import retrieve_candidates


async def run_map_cli(
    topic: str,
    max_papers: int = 25,
    min_year: int | None = None,
    max_year: int | None = None,
    open_access_only: bool = False,
    min_citations: int = 0,
    run_extract: bool = False,
    run_analyze: bool = False,
) -> None:
    """Run retrieval, ranking, extraction, and analysis pipeline and print results."""
    print("=" * 80)
    print(f" Ereuna CLI — Map Generation")
    print(f" Topic: '{topic}'")
    print("=" * 80)

    filters = MapFilters(
        year_min=min_year,
        year_max=max_year,
        open_access_only=open_access_only,
        min_citations=min_citations,
        max_papers=max_papers,
    )

    total_stages = 8 if run_analyze else (5 if run_extract else 3)
    start_time = time.time()

    # Stage 1: Query expansion
    print(f"\n[1/{total_stages}] Expanding research query...")
    queries = await expand_query(topic)
    print(f"      Generated {len(queries)} search phrases:")
    for i, q in enumerate(queries, 1):
        print(f"      {i}. \"{q}\"")

    # Stage 2: Parallel retrieval & deduplication
    print(f"\n[2/{total_stages}] Querying OpenAlex, Semantic Scholar, and arXiv in parallel...")
    candidates = await retrieve_candidates(queries)
    print(f"      Retrieved {len(candidates)} unique candidate papers after DOI and title deduplication.")

    if not candidates:
        print("\n[!] No candidates found. Check internet connection or API settings.")
        return

    # Stage 3: Ranking
    print(f"\n[3/{total_stages}] Scoring and ranking papers by semantic similarity, citations, and recency...")
    ranked = rank_and_filter_papers(topic, candidates, filters=filters, max_papers=max_papers)
    elapsed = time.time() - start_time
    print(f"      Done in {elapsed:.2f}s. Top {len(ranked)} papers selected:\n")

    papers_to_display = ranked

    # Stage 4-5: Full-text fetch + Extraction (if requested or analyze is set)
    if run_extract or run_analyze:
        print(f"\n[4/{total_stages}] Fetching full text for papers with OA links...")
        papers_with_text = await fetch_fulltext(
            ranked,
            on_progress=lambda msg: print(f"      {msg}"),
        )

        full_text_count = sum(1 for p in papers_with_text if p.has_full_text)
        abstract_count = len(papers_with_text) - full_text_count
        print(f"      Full text: {full_text_count}. Abstract-only: {abstract_count}.")

        print(f"\n[5/{total_stages}] Extracting structured data with evidence verification...")
        extracted_papers, reports = await extract(
            papers_with_text,
            on_progress=lambda msg: print(f"      {msg}"),
        )

        total_items = sum(r.total_items for r in reports)
        verified_items = sum(r.verified_items for r in reports)
        dropped_items = sum(r.dropped_items for r in reports)
        pass_rate = (verified_items / total_items * 100) if total_items > 0 else 0.0

        print(f"\n      Evidence Verification Report:")
        print(f"      ├─ Total items extracted: {total_items}")
        print(f"      ├─ Verified (quote in text): {verified_items}")
        print(f"      ├─ Dropped (unverifiable): {dropped_items}")
        print(f"      └─ Pass rate: {pass_rate:.1f}%")

        papers_to_display = extracted_papers
        elapsed = time.time() - start_time

    # Stages 6-8: Analysis, Graph, Experiments
    if run_analyze:
        from app.pipeline.analyze import analyze
        from app.pipeline.experiments import suggest_experiments
        from app.pipeline.graph import build_graph

        print(f"\n[6/{total_stages}] Performing cross-paper analysis...")
        analysis_res = await analyze(papers_to_display, topic)
        contradictions = analysis_res["contradictions"]
        gaps = analysis_res["gaps"]
        tools = analysis_res["tools"]

        print(f"      ├─ Contradictions flagged: {len(contradictions)}")
        print(f"      ├─ Quantified gaps found: {len(gaps)}")
        print(f"      └─ Tools & datasets aggregated: {len(tools)}")

        print(f"\n[7/{total_stages}] Building citation network & computing force layout...")
        graph_data = await build_graph(papers_to_display)
        print(f"      Positioned {len(graph_data.nodes)} nodes with {len(graph_data.edges)} directed edges.")

        print(f"\n[8/{total_stages}] Formulating actionable experiments for gaps...")
        experiments = await suggest_experiments(gaps, papers_to_display)
        print(f"      Formulated {len(experiments)} experimental protocols.")

        # Print Contradictions Summary
        if contradictions:
            print("\n" + "=" * 80)
            print(" CROSS-PAPER CONTRADICTIONS & DIVERGENCES")
            print("=" * 80)
            for c in contradictions:
                print(f"\n[*] {c.metric.upper()} ({c.id})")
                print(f"    Cause: {c.likely_reason}")
                for ent in c.entries:
                    conds = ", ".join(f"{k}: {v}" for k, v in ent.conditions.items() if v)
                    print(f"    - Paper {ent.paper_id}: {ent.value} {ent.unit} ({conds})")

        # Print Quantified Gaps Summary
        if gaps:
            print("\n" + "=" * 80)
            print(" QUANTIFIED RESEARCH GAPS")
            print("=" * 80)
            for g in gaps:
                print(f"\n[*] {g.statement}")
                print(f"    Quantified: {g.pattern}")
                print(f"    Impact:     {g.why_it_matters}")

        # Print Experiments Summary
        if experiments:
            print("\n" + "=" * 80)
            print(" SUGGESTED EXPERIMENTS")
            print("=" * 80)
            for exp in experiments:
                print(f"\n[*] [{exp.id.upper()}] For {exp.gap_id.upper()}: {exp.hypothesis}")
                print(f"    Setup:     {exp.setup[:100]}...")
                print(f"    Tools:     {', '.join(exp.tools)}")
                print(f"    Target:    {exp.expected_result}")
                print(f"    Difficulty:{exp.difficulty}")

    # Print results table
    print()
    print("-" * 100)
    print(f"{'#':<3} {'Score':<6} {'Label':<15} {'Year':<5} {'Cites':<6} {'OA':<4} {'Title'}")
    print("-" * 100)

    for i, p in enumerate(papers_to_display, 1):
        oa_str = "OA" if p.has_full_text or (p.oa_pdf_url is not None) else "--"
        cites_str = str(p.citation_count)
        year_str = str(p.year) if p.year > 0 else "n.d."
        title_trunc = p.title if len(p.title) <= 60 else p.title[:57] + "..."

        print(f"{i:<3} {p.relevance_score:<6.3f} {p.short_label:<15} {year_str:<5} {cites_str:<6} {oa_str:<4} {title_trunc}")
        if p.venue:
            print(f"    Venue: {p.venue}")
        if p.doi:
            print(f"    DOI:   {p.doi}")
        print(f"    Why:   {p.relevance_reason}")

        if (run_extract or run_analyze) and p.extraction:
            ext = p.extraction
            parts = []
            if ext.metrics:
                parts.append(f"{len(ext.metrics)} metrics")
            if ext.findings:
                parts.append(f"{len(ext.findings)} findings")
            if ext.tools:
                parts.append(f"{len(ext.tools)} tools")
            if parts:
                print(f"    Ext:   {', '.join(parts)}")

        print()

    print("-" * 100)
    print(f"Total: {len(papers_to_display)} papers (Finished in {time.time() - start_time:.1f}s)")
    print("-" * 100)


def main() -> None:
    """CLI entrypoint."""
    parser = argparse.ArgumentParser(
        prog="python -m app.cli",
        description="Ereuna CLI: Search and rank academic papers into structured maps.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # 'map' subcommand
    map_parser = subparsers.add_parser("map", help="Generate a research map for a topic")
    map_parser.add_argument("topic", type=str, help="Research topic or question")
    map_parser.add_argument("--max-papers", type=int, default=25, help="Number of papers to select (default: 25)")
    map_parser.add_argument("--min-year", type=int, default=None, help="Earliest publication year")
    map_parser.add_argument("--max-year", type=int, default=None, help="Latest publication year")
    map_parser.add_argument("--min-citations", type=int, default=0, help="Minimum citation threshold")
    map_parser.add_argument("--open-access", action="store_true", help="Require open-access full text")
    map_parser.add_argument("--extract", action="store_true", help="Run stages 4-5: full-text fetch + extraction")
    map_parser.add_argument("--analyze", action="store_true", help="Run stages 1-8: full extraction, analysis, graph, and experiments")

    args = parser.parse_args()

    if args.command == "map":
        asyncio.run(
            run_map_cli(
                topic=args.topic,
                max_papers=args.max_papers,
                min_year=args.min_year,
                max_year=args.max_year,
                open_access_only=args.open_access,
                min_citations=args.min_citations,
                run_extract=args.extract,
                run_analyze=args.analyze,
            )
        )


if __name__ == "__main__":
    main()

