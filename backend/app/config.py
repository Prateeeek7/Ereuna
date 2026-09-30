"""Application configuration from environment variables."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Ereuna backend settings. All values come from environment variables."""

    # Database. Paste Neon's connection string as shown (postgresql://...?sslmode=require).
    # Empty: a local SQLite file (data/researchradar.db) for development.
    database_url: str = ""

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # GROBID
    grobid_url: str = "http://localhost:8070"

    # LLM
    # Ordered fallback chain of free-tier presets, e.g. "cerebras,groq". Each needs
    # its <NAME>_API_KEY. When empty, the single provider below (LLM_*) is used.
    llm_chain: str = ""
    cerebras_api_key: str = ""
    cerebras_model: str = ""
    groq_api_key: str = ""
    groq_model: str = ""
    gemini_api_key: str = ""
    gemini_model: str = ""

    # Provider: openai | anthropic | gemini. "openai" also covers any
    # OpenAI-compatible server (OpenRouter, Groq, Ollama, vLLM) via llm_base_url.
    llm_provider: str = "openai"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o"
    llm_base_url: str = ""
    llm_max_concurrency: int = 4
    llm_timeout_seconds: float = 180.0
    # Characters of paper body sent per extraction call (~4 chars per token).
    llm_max_input_chars: int = 60000
    # Provider token budget per minute (e.g. 8000 on Groq's free tier). 0 = no pacing.
    llm_tokens_per_minute: int = 0
    # Reserved for the model's reply when budgeting tokens.
    llm_max_output_tokens: int = 2500
    # Extract only the top-N ranked papers with the LLM; the rest use heuristics. 0 = all.
    llm_max_papers: int = 0
    # Optional "low" | "medium" | "high" for reasoning models (e.g. gpt-oss on Groq).
    llm_reasoning_effort: str = ""

    # Paper source API keys
    openalex_email: str = ""
    # Free key from https://openalex.org — without it requests share a small
    # per-IP daily budget and start failing with HTTP 429.
    openalex_api_key: str = ""
    semantic_scholar_api_key: str = ""
    # Semantic Scholar's introductory key limit is 1 request/second on all
    # endpoints. Raise only if S2 grants a higher limit for your key.
    semantic_scholar_rps: float = 1.0
    crossref_email: str = ""
    unpaywall_email: str = ""

    # Accounts
    # Legacy local accounts file. Imported into the database once, if the database has no users.
    auth_db_path: str = "data/users.db"
    # Token signing key. When empty, a random key is generated once and kept in the database.
    auth_secret: str = ""
    # New research maps each account may start per day (protects free API budgets). 0 = unlimited.
    max_maps_per_user_per_day: int = 20

    # App settings
    max_papers_default: int = 25
    cache_ttl_days: int = 7
    log_level: str = "INFO"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
