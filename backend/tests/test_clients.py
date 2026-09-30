"""Tests for external API clients using recorded HTTP fixtures."""

import json
from pathlib import Path
import pytest
import xml.etree.ElementTree as ET

from app.clients.arxiv_client import NAMESPACES, parse_arxiv_entry
from app.clients.cache import ResponseCache
from app.clients.openalex import reconstruct_abstract
from app.pipeline.retrieve import arxiv_to_paper, openalex_to_paper, semantic_scholar_to_paper

FIXTURES_DIR = Path(__file__).parent / "fixtures"


def test_reconstruct_abstract():
    inverted_index = {
        "Low-leakage": [0],
        "SRAM": [1],
        "design": [2],
        "using": [3],
        "FinFET": [4],
    }
    assert reconstruct_abstract(inverted_index) == "Low-leakage SRAM design using FinFET"
    assert reconstruct_abstract(None) == ""
    assert reconstruct_abstract({}) == ""


def test_openalex_fixture_parsing():
    with open(FIXTURES_DIR / "openalex_sram.json", "r") as f:
        data = json.load(f)

    results = data["results"]
    assert len(results) == 2

    p1 = openalex_to_paper(results[0])
    assert p1 is not None
    assert p1.doi == "10.1109/jssc.2020.2987112"
    assert p1.year == 2020
    assert p1.citation_count == 48
    assert "Elena Rostova" in [a.name for a in p1.authors]
    assert p1.has_full_text is True
    assert p1.oa_pdf_url == "https://arxiv.org/pdf/2004.00001.pdf"
    assert "independent-gate 7nm FinFET SRAM" in p1.abstract
    assert p1.short_label == "Rostova '20"

    p2 = openalex_to_paper(results[1])
    assert p2 is not None
    assert p2.citation_count == 32
    assert p2.has_full_text is False


def test_semantic_scholar_fixture_parsing():
    with open(FIXTURES_DIR / "semantic_scholar_sram.json", "r") as f:
        data = json.load(f)

    papers = data["data"]
    assert len(papers) == 2

    p1 = semantic_scholar_to_paper(papers[0])
    assert p1 is not None
    assert p1.doi == "10.1109/jssc.2020.2987112"
    assert p1.citation_count == 51
    assert p1.has_full_text is True
    assert p1.venue == "IEEE J. Solid-State Circuits"
    assert p1.short_label == "Rostova '20"

    p2 = semantic_scholar_to_paper(papers[1])
    assert p2 is not None
    assert p2.doi == "10.1145/3386569.3392410"
    assert p2.has_full_text is False


def test_arxiv_fixture_parsing():
    with open(FIXTURES_DIR / "arxiv_sram.xml", "r") as f:
        xml_text = f.read()

    root = ET.fromstring(xml_text)
    entries = root.findall("atom:entry", NAMESPACES)
    assert len(entries) == 2

    parsed1 = parse_arxiv_entry(entries[0])
    paper1 = arxiv_to_paper(parsed1)
    assert paper1 is not None
    assert paper1.doi == "10.1109/jssc.2020.2987112"
    assert paper1.year == 2020
    assert paper1.has_full_text is True
    assert "https://arxiv.org/pdf/2004.00001.pdf" in paper1.oa_pdf_url

    parsed2 = parse_arxiv_entry(entries[1])
    paper2 = arxiv_to_paper(parsed2)
    assert paper2 is not None
    assert paper2.year == 2023
    assert paper2.short_label == "O'Connor '23"


@pytest.mark.asyncio
async def test_response_cache_sqlite(tmp_path):
    db_file = tmp_path / "test_cache.db"
    cache = ResponseCache(db_path=str(db_file))

    # Test cache miss
    val = await cache.get("test_source", "/endpoint", {"q": "sram"})
    assert val is None

    # Test cache write & hit
    payload = {"results": [1, 2, 3]}
    await cache.set("test_source", "/endpoint", payload, {"q": "sram"}, ttl_seconds=3600)

    cached = await cache.get("test_source", "/endpoint", {"q": "sram"})
    assert cached == payload

    # Different params -> miss
    diff = await cache.get("test_source", "/endpoint", {"q": "finfet"})
    assert diff is None


def test_europepmc_find_pmcid_and_parse_jats():
    from app.clients.europepmc import find_pmcid, parse_jats

    assert find_pmcid(["https://www.ncbi.nlm.nih.gov/pmc/articles/6657343"]) == "PMC6657343"
    assert find_pmcid(["https://europepmc.org/articles/PMC4714946?pdf=render"]) == "PMC4714946"
    assert find_pmcid(["https://www.nature.com/articles/x.pdf"]) is None

    xml = """<article><front><article-meta><abstract><p>We edit <italic>genes</italic>.</p></abstract>
    </article-meta></front><body><sec><title>Results</title><p>Editing efficiency was 42%.</p>
    <sec><title>Sub</title><p>Off-target rate was low.</p></sec></sec></body></article>"""
    parsed = parse_jats(xml)
    assert [s.heading for s in parsed.sections] == ["Abstract", "Results"]
    assert "We edit genes." in parsed.full_text
    assert "Editing efficiency was 42%." in parsed.full_text
    assert "Off-target rate was low." in parsed.full_text


def test_llm_retry_after_parsing():
    import httpx
    from app.clients.llm import _retry_after_seconds

    req = httpx.Request("POST", "https://api.groq.com/openai/v1/chat/completions")
    assert _retry_after_seconds(httpx.Response(429, headers={"retry-after": "7"}, request=req)) == 7.0
    body = '{"error":{"message":"Rate limit reached ... Please try again in 4.86s."}}'
    assert _retry_after_seconds(httpx.Response(429, text=body, request=req)) == 4.86
    body = '{"error":{"message":"Please try again in 1m2.5s."}}'
    assert _retry_after_seconds(httpx.Response(429, text=body, request=req)) == 62.5
    assert _retry_after_seconds(httpx.Response(429, text="nope", request=req)) is None


def test_token_budget_paces_requests(monkeypatch):
    import asyncio
    from app.clients import llm as llm_mod

    budget = llm_mod._TokenBudget(tokens_per_minute=1000)

    async def run():
        await budget.acquire(600)
        # A second 600-token request must wait for the first to leave the window.
        try:
            await asyncio.wait_for(budget.acquire(600), timeout=0.3)
            return False
        except asyncio.TimeoutError:
            return True

    assert asyncio.run(run())



def test_token_budget_limits_requests_per_minute():
    import asyncio
    from app.clients import llm as llm_mod

    budget = llm_mod._TokenBudget(requests_per_minute=2)

    async def run():
        await budget.acquire(10)
        await budget.acquire(10)
        try:
            await asyncio.wait_for(budget.acquire(10), timeout=0.3)
            return False
        except asyncio.TimeoutError:
            return True

    assert asyncio.run(run())


def test_llm_chain_falls_back_to_next_provider(monkeypatch):
    import asyncio
    import httpx
    from app.clients import llm as llm_mod
    from app.clients.llm import LlmClient, ProviderConfig
    from pydantic import BaseModel

    class Out(BaseModel):
        ok: bool

    first = ProviderConfig(name="first_t", kind="openai", model="m1", api_key="k1", max_input_chars=500)
    second = ProviderConfig(name="second_t", kind="openai", model="m2", api_key="k2", max_input_chars=100)
    monkeypatch.setattr(llm_mod, "build_providers", lambda: [first, second])
    llm_mod._cooldown_until.clear()

    seen: list[tuple[str, int]] = []

    async def fake_call(self, p, prompt):
        seen.append((p.name, len(prompt.split("\n\n")[0])))
        if p.name == "first_t":
            req = httpx.Request("POST", "https://x")
            resp = httpx.Response(429, text='{"error":"Tokens per day limit exceeded"}', request=req)
            raise httpx.HTTPStatusError("429", request=req, response=resp)
        return '{"ok": true}'

    monkeypatch.setattr(LlmClient, "_call", fake_call)

    async def run():
        client = LlmClient()
        result = await client.structured(lambda n: "x" * n, Out)
        # The exhausted provider is skipped on the next request without being called.
        seen.clear()
        await client.structured(lambda n: "x" * n, Out)
        return result, client.last_model

    result, last_model = asyncio.run(run())
    assert result.ok is True
    assert last_model == "m2"
    assert seen == [("second_t", 100)]  # prompt re-sized to the second provider's budget
    llm_mod._cooldown_until.clear()



def test_chain_expands_multiple_models_per_provider(monkeypatch):
    from app.clients import llm as llm_mod

    monkeypatch.setattr(llm_mod.settings, "llm_chain", "gemini,groq")
    monkeypatch.setattr(llm_mod.settings, "gemini_api_key", "g-key")
    monkeypatch.setattr(llm_mod.settings, "gemini_model", "flash-a, lite-b")
    monkeypatch.setattr(llm_mod.settings, "groq_api_key", "")
    providers = llm_mod.build_providers()
    assert [(p.name, p.model) for p in providers] == [("gemini", "flash-a"), ("gemini:lite-b", "lite-b")]
    assert all(p.kind == "gemini" for p in providers)



def test_extract_json_handles_wrapping_and_trailing_text():
    from app.clients.llm import _extract_json
    import json

    assert json.loads(_extract_json('{"a": 1}\n{"b": 2}')) == {"a": 1}
    assert json.loads(_extract_json('Here you go:\n```json\n{"a": [1, 2]}\n```')) == {"a": [1, 2]}
    assert json.loads(_extract_json('{"a": "}"} trailing')) == {"a": "}"}
