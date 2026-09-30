"""LLM client — provider-agnostic interface for structured JSON extraction.

Supports OpenAI-compatible APIs (OpenAI, Groq, Cerebras, OpenRouter, Ollama, ...),
Anthropic, and Google Gemini. Several providers can be chained (LLM_CHAIN): each
request goes to the first provider with budget left, and moves to the next one
when a provider's rate or daily limit is exhausted, so free tiers can be stacked.

Always requests JSON output matching a Pydantic schema; validates and retries.

Usage:
    client = LlmClient()
    result = await client.structured(prompt, MyPydanticModel)
"""

import asyncio
import json
import logging
import re
import time
from dataclasses import dataclass
from typing import Callable, Optional, TypeVar, Union

import httpx
from pydantic import BaseModel, ValidationError

from app.config import settings

T = TypeVar("T", bound=BaseModel)

# A prompt, or a function that builds the prompt for a given paper-text budget
# (characters), so each provider in the chain gets text sized to its limits.
PromptSpec = Union[str, Callable[[int], str]]

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "You are a scientific literature analysis assistant. "
    "You extract structured information from academic papers. "
    "Always respond with valid JSON matching the requested schema. "
    "Every extracted item must include a verbatim quote from the source text as evidence. "
    "Never invent facts, numbers, papers, or quotes that are not in the provided text."
)

_LOCAL_HOSTS = ("localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal", "ollama")


@dataclass
class ProviderConfig:
    """One LLM endpoint and the limits to respect when calling it."""

    name: str
    kind: str  # "openai" (OpenAI-compatible) | "anthropic" | "gemini"
    model: str
    api_key: str = ""
    base_url: str = ""
    tokens_per_minute: int = 0  # 0 = no pacing
    requests_per_minute: int = 0  # 0 = no pacing
    max_input_chars: int = 60000
    max_output_tokens: int = 2500
    reasoning_effort: str = ""

    def is_usable(self) -> bool:
        if not self.model:
            return False
        if self.api_key:
            return True
        return self.kind == "openai" and any(h in self.base_url for h in _LOCAL_HOSTS)


# Free-tier presets for LLM_CHAIN. Limits come from each provider's published
# free-tier limits / response headers; override the model with <NAME>_MODEL.
_PRESETS: dict[str, dict] = {
    "cerebras": dict(
        kind="openai", base_url="https://api.cerebras.ai/v1", model="gpt-oss-120b",
        tokens_per_minute=30000, requests_per_minute=5, max_input_chars=20000,
        max_output_tokens=3000, reasoning_effort="low",
    ),
    "groq": dict(
        kind="openai", base_url="https://api.groq.com/openai/v1", model="openai/gpt-oss-120b",
        tokens_per_minute=8000, requests_per_minute=30, max_input_chars=10000,
        max_output_tokens=3000, reasoning_effort="low",
    ),
    "gemini": dict(
        kind="gemini", base_url="https://generativelanguage.googleapis.com/v1beta",
        model="gemini-flash-latest", tokens_per_minute=0, requests_per_minute=10,
        max_input_chars=40000, max_output_tokens=8192, reasoning_effort="low",
    ),
}


def build_providers() -> list[ProviderConfig]:
    """Providers in the order they should be tried."""
    chain = [n.strip().lower() for n in settings.llm_chain.split(",") if n.strip()]
    providers: list[ProviderConfig] = []
    for name in chain:
        preset = _PRESETS.get(name)
        if preset is None:
            logger.warning("Unknown provider '%s' in LLM_CHAIN (known: %s)", name, ", ".join(_PRESETS))
            continue
        key = getattr(settings, f"{name}_api_key", "")
        # <NAME>_MODEL may list several models, tried in order (e.g. a strong
        # model that is often overloaded on free tiers, then a lighter one).
        models = [m.strip() for m in (getattr(settings, f"{name}_model", "") or preset["model"]).split(",") if m.strip()]
        for i, model in enumerate(models or [""]):
            cfg = ProviderConfig(
                name=name if i == 0 else f"{name}:{model}",
                **{**preset, "model": model, "api_key": key.strip()},
            )
            if cfg.is_usable():
                providers.append(cfg)
            else:
                logger.info("Skipping '%s' in LLM_CHAIN: no API key or model configured", name)
    if providers:
        return providers

    # Single provider configured through the generic LLM_* settings.
    kind = settings.llm_provider.lower().strip()
    kind = {"openrouter": "openai", "ollama": "openai", "claude": "anthropic", "google": "gemini"}.get(kind, kind)
    single = ProviderConfig(
        name=settings.llm_provider.lower().strip() or "openai",
        kind=kind,
        model=settings.llm_model.strip(),
        api_key=settings.llm_api_key.strip(),
        base_url=settings.llm_base_url.strip().rstrip("/"),
        tokens_per_minute=settings.llm_tokens_per_minute,
        requests_per_minute=0,
        max_input_chars=settings.llm_max_input_chars,
        max_output_tokens=settings.llm_max_output_tokens,
        reasoning_effort=settings.llm_reasoning_effort,
    )
    return [single] if single.is_usable() else []


class _TokenBudget:
    """Sliding one-minute budget of tokens and requests for one provider.

    Pacing requests up front avoids burning retries on 429s from providers
    with per-minute limits (e.g. free tiers)."""

    def __init__(self, tokens_per_minute: int = 0, requests_per_minute: int = 0) -> None:
        self.tpm = tokens_per_minute
        self.rpm = requests_per_minute
        self._events: list[list] = []  # [time, tokens]
        self._locks: dict[int, asyncio.Lock] = {}

    def _prune(self, now: float) -> None:
        self._events = [e for e in self._events if now - e[0] < 60.0]

    async def acquire(self, tokens: int) -> list:
        if self.tpm <= 0 and self.rpm <= 0:
            return [0.0, 0]
        if self.tpm > 0:
            tokens = min(tokens, self.tpm)
        lock = self._locks.setdefault(id(asyncio.get_running_loop()), asyncio.Lock())
        async with lock:
            loop = asyncio.get_running_loop()
            while True:
                now = loop.time()
                self._prune(now)
                used = sum(e[1] for e in self._events)
                tokens_ok = self.tpm <= 0 or used + tokens <= self.tpm
                requests_ok = self.rpm <= 0 or len(self._events) < self.rpm
                if tokens_ok and requests_ok:
                    event = [now, tokens]
                    self._events.append(event)
                    return event
                await asyncio.sleep(max(0.5, 60.0 - (now - self._events[0][0]) + 0.1))

    def wait_seconds(self, tokens: int) -> float:
        """How long acquire(tokens) would wait right now (0 when it can go at once)."""
        if self.tpm <= 0 and self.rpm <= 0:
            return 0.0
        now = time.monotonic()
        events = [e for e in self._events if now - e[0] < 60.0]
        used = sum(e[1] for e in events)
        tokens = min(tokens, self.tpm) if self.tpm > 0 else tokens
        if (self.tpm <= 0 or used + tokens <= self.tpm) and (self.rpm <= 0 or len(events) < self.rpm):
            return 0.0
        return max(0.0, 60.0 - (now - events[0][0])) if events else 0.0

    @staticmethod
    def correct(event: list, actual_tokens: Optional[int]) -> None:
        """Replace the estimate with the provider-reported usage."""
        if actual_tokens:
            event[1] = actual_tokens


# Process-wide state per provider name: pacing budgets and cool-downs after a
# provider reports that its (e.g. daily) limit is exhausted.
_budgets: dict[str, _TokenBudget] = {}
_cooldown_until: dict[str, float] = {}
_semaphores: dict[int, asyncio.Semaphore] = {}


def _budget_for(p: ProviderConfig) -> _TokenBudget:
    b = _budgets.get(p.name)
    if b is None or (b.tpm, b.rpm) != (p.tokens_per_minute, p.requests_per_minute):
        b = _TokenBudget(p.tokens_per_minute, p.requests_per_minute)
        _budgets[p.name] = b
    return b


def _shared_semaphore() -> asyncio.Semaphore:
    loop_id = id(asyncio.get_running_loop())
    sem = _semaphores.get(loop_id)
    if sem is None:
        sem = asyncio.Semaphore(max(1, settings.llm_max_concurrency))
        _semaphores[loop_id] = sem
    return sem


def _estimate_tokens(text: str, max_output_tokens: int) -> int:
    return len(text) // 3 + max_output_tokens


class ProviderUnavailable(Exception):
    """This provider cannot serve the request now; try the next one."""


def _retry_after_seconds(resp: httpx.Response) -> Optional[float]:
    """Seconds to wait from a 429 response (Retry-After header or 'try again in 4.2s')."""
    header = resp.headers.get("retry-after")
    if header:
        try:
            return float(header)
        except ValueError:
            pass
    m = re.search(r"try again in (?:(\d+)m)?([\d.]+)s", resp.text)
    if m:
        return float(m.group(1) or 0) * 60 + float(m.group(2))
    return None


def _extract_json(raw: str) -> str:
    """The first complete JSON object in a reply, ignoring markdown fences,
    leading prose, or trailing text some models add."""
    text = raw.strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
    if fence:
        text = fence.group(1).strip()
    start = text.find("{")
    if start == -1:
        return text
    try:
        _, end = json.JSONDecoder().raw_decode(text, start)
        return text[start:end]
    except json.JSONDecodeError:
        end = text.rfind("}")
        return text[start : end + 1] if end > start else text[start:]


def _salvage(parsed: object, schema: type[T]) -> Optional[T]:
    """Drop individual list items that fail validation (e.g. a metric with a null
    value) instead of discarding the whole response. Every kept item is still
    evidence-verified downstream, so this never admits unverified content."""
    if not isinstance(parsed, dict):
        return None
    data = json.loads(json.dumps(parsed))
    for _ in range(10):
        try:
            return schema.model_validate(data)
        except ValidationError as e:
            removals: dict[tuple, set[int]] = {}
            for err in e.errors():
                loc = err.get("loc", ())
                idx = next((i for i, part in enumerate(loc) if isinstance(part, int)), None)
                if idx is None:
                    return None
                removals.setdefault(tuple(loc[:idx]), set()).add(loc[idx])
            for path, indices in removals.items():
                container = data
                for key in path:
                    container = container[key]
                if not isinstance(container, list):
                    return None
                for i in sorted(indices, reverse=True):
                    if 0 <= i < len(container):
                        del container[i]
    return None


class LlmClient:
    """Provider-agnostic LLM client with an ordered fallback chain.

    Always requests JSON output matching a Pydantic schema, validates it, and
    retries up to 2 times on invalid output. Rate-limit waits don't count as
    retries; an exhausted provider (daily cap, auth error, repeated failures)
    is skipped in favour of the next one in the chain.
    """

    def __init__(self) -> None:
        self._providers = build_providers()
        self._client: Optional[httpx.AsyncClient] = None
        self._last_usage: Optional[int] = None
        self.last_model: str = ""

    # ── Introspection ────────────────────────────────────────────────
    def _active(self) -> Optional[ProviderConfig]:
        now = time.time()
        for p in self._providers:
            if _cooldown_until.get(p.name, 0) <= now:
                return p
        return self._providers[0] if self._providers else None

    @property
    def provider(self) -> str:
        return " → ".join(p.name.split(":")[0] for p in self._providers) or settings.llm_provider

    def describe(self) -> str:
        """Human-readable chain, e.g. 'cerebras (gpt-oss-120b) → gemini (gemini-flash-latest, ...)'."""
        groups: dict[str, list[str]] = {}
        for p in self._providers:
            groups.setdefault(p.name.split(":")[0], []).append(p.model)
        return " → ".join(f"{name} ({', '.join(models)})" for name, models in groups.items())

    @property
    def model(self) -> str:
        active = self._active()
        return active.model if active else settings.llm_model

    @property
    def max_input_chars(self) -> int:
        """Paper text budget of the provider currently first in line."""
        active = self._active()
        return active.max_input_chars if active else settings.llm_max_input_chars

    def is_configured(self) -> bool:
        return bool(self._providers)

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=settings.llm_timeout_seconds)
        return self._client

    def _build_schema_instruction(self, schema: type[T]) -> str:
        schema_dict = schema.model_json_schema()
        return (
            "You MUST respond with valid JSON matching this schema exactly. "
            "Do not include any text before or after the JSON object.\n\n"
            f"JSON Schema:\n```json\n{json.dumps(schema_dict, indent=2)}\n```"
        )

    # ── Provider calls ───────────────────────────────────────────────
    async def _call_openai(self, p: ProviderConfig, prompt: str) -> str:
        headers = {"Content-Type": "application/json"}
        if p.api_key:
            headers["Authorization"] = f"Bearer {p.api_key}"
        body = {
            "model": p.model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "max_tokens": p.max_output_tokens,
        }
        if p.reasoning_effort:
            body["reasoning_effort"] = p.reasoning_effort
        base_url = p.base_url or "https://api.openai.com/v1"
        resp = await self._get_client().post(f"{base_url}/chat/completions", headers=headers, json=body)
        resp.raise_for_status()
        data = resp.json()
        self._last_usage = (data.get("usage") or {}).get("total_tokens")
        return data["choices"][0]["message"]["content"]

    async def _call_anthropic(self, p: ProviderConfig, prompt: str) -> str:
        headers = {
            "x-api-key": p.api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        body = {
            "model": p.model,
            "max_tokens": max(p.max_output_tokens, 4096),
            "temperature": 0,
            "system": SYSTEM_PROMPT,
            "messages": [{"role": "user", "content": prompt}],
        }
        base_url = p.base_url or "https://api.anthropic.com"
        resp = await self._get_client().post(f"{base_url}/v1/messages", headers=headers, json=body)
        resp.raise_for_status()
        data = resp.json()
        usage = data.get("usage") or {}
        self._last_usage = (usage.get("input_tokens") or 0) + (usage.get("output_tokens") or 0) or None
        return "".join(b.get("text", "") for b in data.get("content", []) if b.get("type") == "text")

    async def _call_gemini(self, p: ProviderConfig, prompt: str) -> str:
        base_url = p.base_url or "https://generativelanguage.googleapis.com/v1beta"
        body = {
            "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0,
                "responseMimeType": "application/json",
                "maxOutputTokens": p.max_output_tokens,
            },
        }
        if p.reasoning_effort:
            body["generationConfig"]["thinkingConfig"] = {"thinkingLevel": p.reasoning_effort}
        resp = await self._get_client().post(
            f"{base_url}/models/{p.model}:generateContent", json=body, headers={"x-goog-api-key": p.api_key}
        )
        resp.raise_for_status()
        data = resp.json()
        self._last_usage = (data.get("usageMetadata") or {}).get("totalTokenCount")
        parts = (data["candidates"][0].get("content") or {}).get("parts") or []
        return "".join(part.get("text", "") for part in parts if not part.get("thought"))

    async def _call(self, p: ProviderConfig, prompt: str) -> str:
        if p.kind == "openai":
            return await self._call_openai(p, prompt)
        if p.kind == "anthropic":
            return await self._call_anthropic(p, prompt)
        if p.kind == "gemini":
            return await self._call_gemini(p, prompt)
        raise ValueError(f"Unsupported LLM provider kind: {p.kind}")

    # ── One provider, with pacing and retries ────────────────────────
    async def _call_with_limits(self, p: ProviderConfig, prompt: str) -> str:
        """Raw text from provider p, or ProviderUnavailable if it can't serve now."""
        budget = _budget_for(p)
        waits = 0
        failures = 0
        while True:
            estimate = _estimate_tokens(prompt, p.max_output_tokens)
            event = await budget.acquire(estimate)
            self._last_usage = None
            try:
                raw = await self._call(p, prompt)
                _TokenBudget.correct(event, self._last_usage)
                return raw
            except httpx.HTTPStatusError as e:
                status = e.response.status_code
                text = e.response.text[:300]
                if status == 429:
                    wait = _retry_after_seconds(e.response)
                    daily = bool(re.search(r"(?i)per day|daily|tokens.day|requests.day|quota", text))
                    if wait is not None and wait <= 90 and not daily and waits < 6:
                        waits += 1
                        logger.info("%s rate limited; waiting %.1fs as the provider asks", p.name, wait)
                        await asyncio.sleep(wait + 0.5)
                        continue
                    cool = wait if wait and wait > 90 else (3600.0 if daily else 60.0)
                    _cooldown_until[p.name] = time.time() + min(cool, 86400.0)
                    raise ProviderUnavailable(f"{p.name} limit reached (HTTP 429): {text}")
                if status in (401, 402, 403):
                    _cooldown_until[p.name] = time.time() + 3600.0
                    raise ProviderUnavailable(f"{p.name} rejected the request (HTTP {status}): {text}")
                if status == 413:
                    raise ProviderUnavailable(f"{p.name}: request too large (HTTP 413)")
                if status in (408, 500, 502, 503, 504, 529):
                    if failures < 2:
                        failures += 1
                        await asyncio.sleep(2 ** failures)
                        continue
                    # Overloaded / failing: skip it for a couple of minutes rather
                    # than paying the retry delay on every request.
                    _cooldown_until[p.name] = time.time() + 120.0
                raise ProviderUnavailable(f"{p.name} failed (HTTP {status}): {text}")
            except (httpx.TimeoutException, httpx.TransportError) as e:
                if failures < 2:
                    failures += 1
                    await asyncio.sleep(2)
                    continue
                _cooldown_until[p.name] = time.time() + 120.0
                raise ProviderUnavailable(f"{p.name} unreachable: {e}")

    async def _complete(self, prompt: PromptSpec, suffix: str) -> str:
        """Raw text from the first provider in the chain that can serve the request."""
        errors: list[str] = []
        now = time.time()
        ready = [p for p in self._providers if _cooldown_until.get(p.name, 0) <= now]
        # Free tiers stack: when the preferred provider would make us wait for its
        # per-minute budget, a provider that can answer now goes first (stable sort,
        # so the configured order still decides among equals).
        ready.sort(key=lambda p: _budget_for(p).wait_seconds(_estimate_tokens("x" * p.max_input_chars, p.max_output_tokens)) > 3.0)
        # If every provider is cooling down, still try them in order (limits may have reset).
        for p in ready or self._providers:
            text = (prompt(p.max_input_chars) if callable(prompt) else prompt) + suffix
            try:
                async with _shared_semaphore():
                    raw = await self._call_with_limits(p, text)
                self.last_model = p.model
                return raw
            except ProviderUnavailable as e:
                logger.warning("LLM provider unavailable, trying next: %s", str(e)[:300])
                errors.append(str(e)[:200])
        raise RuntimeError("All LLM providers failed: " + " | ".join(errors))

    async def structured(self, prompt: PromptSpec, schema: type[T]) -> T:
        """Send a prompt and parse the response into a Pydantic model.

        `prompt` may be a function of the paper-text budget (characters) so the
        text is sized for whichever provider serves the request.

        Raises:
            ValueError: If the response fails validation after retries.
            RuntimeError: If no provider is configured or all providers failed.
        """
        if not self.is_configured():
            raise RuntimeError("No LLM configured. Set LLM_CHAIN with provider keys, or LLM_API_KEY.")

        suffix = f"\n\n{self._build_schema_instruction(schema)}"
        last_error: Optional[Exception] = None

        for attempt in range(3):  # initial + 2 retries on invalid output
            raw = await self._complete(prompt, suffix)
            try:
                parsed = json.loads(_extract_json(raw))
                try:
                    return schema.model_validate(parsed)
                except ValidationError:
                    salvaged = _salvage(parsed, schema)
                    if salvaged is not None:
                        logger.info("LLM response had invalid items; kept the valid ones")
                        return salvaged
                    raise
            except (json.JSONDecodeError, ValidationError) as e:
                last_error = e
                logger.warning("LLM response validation failed (attempt %d/3): %s", attempt + 1, str(e)[:300])
                suffix += (
                    f"\n\nYour previous response had an error: {str(e)[:500]}. "
                    "Please fix it and respond with valid JSON matching the schema exactly."
                )

        raise ValueError(f"LLM response failed validation after 3 attempts: {last_error}")

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None
