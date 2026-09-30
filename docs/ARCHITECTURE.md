# Ereuna — Architecture

## Overview

Ereuna is a three-tier system: a native Android client, a Python FastAPI backend, and external academic paper sources. The phone never talks to an LLM or paper API directly; all research logic runs server-side.

```
┌──────────────────┐      REST + SSE       ┌──────────────────────┐
│  Android Client  │ ────────────────────▶  │  FastAPI Gateway     │
│  Compose + Room  │ ◀──────────────────── │  (port 8000)         │
└──────────────────┘                       └──────────┬───────────┘
                                                      │
                                           ┌──────────▼───────────┐
                                           │  Arq Worker          │
                                           │  (async job queue)   │
                                           └──────────┬───────────┘
                                                      │
                              ┌────────────────────────┼────────────────────────┐
                              │                        │                        │
                    ┌─────────▼─────────┐   ┌─────────▼─────────┐   ┌─────────▼─────────┐
                    │  Paper Sources     │   │  LLM Provider     │   │  GROBID           │
                    │  OpenAlex          │   │  (structured JSON) │   │  (PDF → sections) │
                    │  Semantic Scholar  │   │                    │   │                    │
                    │  arXiv, Crossref   │   └────────────────────┘   └────────────────────┘
                    │  Unpaywall         │
                    └────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  Postgres          │
                    │  + pgvector        │
                    │  + Redis           │
                    └────────────────────┘
```

## Repo Layout

```
/android   Kotlin, Jetpack Compose app
/backend   Python 3.12 FastAPI service
/docs      This file, DESIGN.md, API.md
```

## Android Client

### Module Structure

```
:app                    Application entry, navigation, Hilt setup
:core:design            Design tokens, typography, theme, signature components
:core:model             Shared Kotlin data classes (@Serializable)
:core:network           Retrofit service, OkHttp, SSE helper
:core:data              Repositories, Room database, DataStore
:feature:search         Search home screen, filters
:feature:map            Map scaffold with tab navigation
:feature:paper          Paper detail screen
:feature:compare        DataTable comparison view
:feature:graph          Citation network graph (Canvas)
:feature:library        Saved maps and papers
:feature:settings       Theme, filters, account, data sources
```

### Dependency Graph

```
:app ──▶ :feature:* ──▶ :core:data ──▶ :core:network
                    ──▶ :core:design       ──▶ :core:model
                    ──▶ :core:model
```

### Key Choices

| Concern          | Choice                                                     |
|------------------|-------------------------------------------------------------|
| Language / UI    | Kotlin 2.0+, Jetpack Compose, Material 3 (heavily restyled)|
| Architecture     | MVVM, unidirectional data flow, StateFlow, immutable UiState|
| DI               | Hilt with KSP                                              |
| Network          | Retrofit + OkHttp + kotlinx.serialization                  |
| Streaming        | OkHttp SSE for job progress                                |
| Local storage    | Room (maps, papers), DataStore (settings)                  |
| Navigation       | Navigation Compose with type-safe routes                   |
| Min SDK          | 26                                                         |
| Concurrency      | Kotlin Coroutines + Flow (no RxJava)                       |

## Backend

### Service Architecture

Five containers orchestrated by docker-compose:

| Service    | Image / Build     | Port  | Purpose                              |
|------------|-------------------|-------|--------------------------------------|
| `api`      | `./backend`       | 8000  | FastAPI gateway, REST + SSE          |
| `worker`   | `./backend`       | —     | Arq async worker, runs pipeline jobs |
| `postgres` | `postgres:16`     | 5432  | Primary storage + pgvector           |
| `redis`    | `redis:7-alpine`  | 6379  | Job queue, response cache            |
| `grobid`   | `lfoppiano/grobid`| 8070  | PDF → structured sections            |

### Storage

`DATABASE_URL` selects the database (`app/db/session.py`); tables are created on startup (`app/db/store.py`).

- **Production: Neon (hosted Postgres).** Paste Neon's connection string as shown; `sslmode`/`channel_binding` are translated for asyncpg, and the `-pooler` endpoint disables prepared-statement caching.
- **Local development and tests: SQLite** (`data/researchradar.db` when `DATABASE_URL` is empty; a throwaway file per test).

| Table          | Holds                                                                  |
|----------------|------------------------------------------------------------------------|
| `users`        | Accounts (scrypt hash + salt)                                           |
| `maps`         | Each research map as gzip JSON (~40 KB), looked up by id or topic+filters cache key |
| `paper_index`  | paper id → map id, so single papers are served from their map           |
| `saved_maps`, `saved_papers` | Per-user libraries                                        |
| `map_quota`    | Maps started per user per UTC day                                       |
| `app_settings` | Generated token signing key (unless `AUTH_SECRET` is set)               |

Parsed full text is used during extraction and not stored; every extracted item keeps its verified quote, page and section. Job progress streams (SSE) stay in memory for the few minutes a map takes to build. On first start against an empty database, accounts from the old `data/users.db` file are imported.

### Pipeline

Eight deterministic stages. The LLM is used only in stages 3, 5, 6, and 8, always with a fixed JSON schema.

```
1. expand_query    topic → 4-6 search queries + synonyms
2. retrieve        query sources in parallel, merge by DOI / normalized title
3. rank            score + LLM re-rank → top N papers with relevance_reason
4. fetch_fulltext  open-access PDF via GROBID; else abstract-only
5. extract         per paper → structured extraction with verbatim evidence
6. analyze         normalize units, comparison matrix, contradictions, gaps
7. graph           citation edges + foundational papers, server-side layout
8. experiments     1-2 per gap: hypothesis, setup, tools, variables
```

Each stage emits SSE events with human-readable log lines.

### Upload mode ("Use my papers")

`POST /v1/maps/upload` (multipart: `topic`, `include_search`, `files`; up to 15 PDFs, 25 MB each) builds a map from the user's own PDFs (`app/pipeline/uploads.py`):

```
1. read_uploads      parse each PDF (GROBID, else PyMuPDF); scanned or protected PDFs are skipped
2. identify_papers   DOI / arXiv id on page 1 → OpenAlex, Crossref; else title → OpenAlex, S2 title match.
                     A record is used only if its title matches the PDF's, so a cited DOI isn't mistaken for the paper.
3–4. find_related    optional: the normal search, ranking and full-text stages; results matching an upload are dropped
5–8.                 the same extraction, analysis, graph and experiments as search mode
```

Uploaded papers (`source: "upload"`) are always read individually by the LLM. The PDFs stay in memory only while parsed and are never written to disk; like all maps, only extracted items with their quotes are stored. Upload maps are private: `owner_id` is set, they are never returned from the topic cache, and maps, papers, graph, exports and library saves return 404 to other accounts. They cannot be refreshed.

### Data Flow

1. Client sends `POST /v1/maps {topic, filters}`.
2. Server checks cache (normalized topic + filters hash, < 7 days). If fresh, returns `{job_id, map_id}` with status `cached`.
3. Otherwise, enqueues an Arq job. Returns `{job_id, map_id}` with status `processing`.
4. Client opens `GET /v1/jobs/{id}/events` for SSE progress.
5. Worker runs pipeline stages 1–8, emitting events at each stage.
6. On completion, client fetches `GET /v1/maps/{id}` for the full research map.
7. Maps and papers are cached in Room for offline access.

### External Clients

Each paper source is wrapped in its own client class with:
- Retry logic (exponential backoff)
- Rate limiting (per-source)
- 24-hour response caching in Redis
- API keys from environment variables

| Client           | Source              | Data Provided                    |
|------------------|---------------------|----------------------------------|
| `OpenAlexClient` | OpenAlex API        | Metadata, references, concepts   |
| `SemanticScholarClient` | S2 Graph API | Citations, abstracts, OA PDFs    |
| `ArxivClient`    | arXiv API           | Preprints, abstracts, PDF links  |
| `CrossrefClient` | Crossref API        | DOI resolution, metadata         |
| `UnpaywallClient`| Unpaywall API       | Legal open-access PDF links      |
| `LlmClient`      | Configurable (env)  | Structured JSON extraction       |

### LLM Interface

```python
class LlmClient:
    async def structured(self, prompt: str, schema: type[T]) -> T:
        """Request JSON matching a Pydantic schema. Validate. Retry up to 2× on failure."""
```

Provider is set by `LLM_PROVIDER` env var. Always requests JSON output matching the provided Pydantic model.
