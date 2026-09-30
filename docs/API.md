# Ereuna — API Reference

Base URL: `http://localhost:8000/v1`

All responses use `snake_case` JSON. Errors return `{"error": {"code": "...", "message": "..."}}`.

---

## Endpoints

### Account

All other endpoints need `Authorization: Bearer <token>` from sign-up or sign-in.

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/v1/auth/signup` | `{name, email, password}` | `201 {token, user}`; 409 if the email exists; 422 if the password lacks 8+ chars, an uppercase letter and a number |
| POST | `/v1/auth/signin` | `{email, password}` | `200 {token, user}`; 401 on wrong credentials |
| GET | `/v1/auth/me` | — | `200 {id, name, email}` |
| POST | `/v1/auth/delete` | `{password}` | `204`; 403 on a wrong password. Deletes the account, its library, quota rows and every map built from its uploads. Topic maps are shared and are kept. |

### Maps

#### `POST /v1/maps`

Create a new research map or return a cached one (if < 7 days old with matching filters).

**Request:**
```json
{
  "topic": "Low-leakage SRAM using FinFET",
  "filters": {
    "year_min": 2015,
    "year_max": 2025,
    "fields": ["electrical engineering", "computer science"],
    "min_citations": 5,
    "open_access_only": false,
    "max_papers": 25
  }
}
```

**Response (202 Accepted):**
```json
{
  "job_id": "job_abc123",
  "map_id": "map_def456",
  "status": "processing"
}
```

**Response (200 OK — cached):**
```json
{
  "job_id": null,
  "map_id": "map_def456",
  "status": "cached"
}
```

---

#### `GET /v1/maps/{map_id}`

Retrieve a complete research map with all associated data.

**Response:**
```json
{
  "id": "map_def456",
  "topic": "Low-leakage SRAM using FinFET",
  "normalized_topic": "low-leakage sram using finfet",
  "filters": { ... },
  "created_at": "2025-01-15T10:30:00Z",
  "updated_at": "2025-01-15T10:35:00Z",
  "stats": {
    "total_papers": 25,
    "full_text_papers": 19,
    "abstract_only_papers": 6,
    "year_range": [2016, 2025],
    "gaps_count": 5,
    "contradictions_count": 2
  },
  "synthesis": {
    "text": "Research on low-leakage SRAM using FinFET technology spans ...",
    "citation_ids": ["paper_001", "paper_003", "paper_012"]
  },
  "papers": [ ... ],
  "findings": [ ... ],
  "gaps": [ ... ],
  "contradictions": [ ... ],
  "experiments": [ ... ],
  "tools": [ ... ]
}
```

---

#### `GET /v1/maps/{map_id}/graph`

Retrieve the citation graph with precomputed layout.

**Response:**
```json
{
  "nodes": [
    {
      "paper_id": "paper_001",
      "x": 120.5,
      "y": 340.2,
      "size": 24.0,
      "year": 2023,
      "in_map": true,
      "label": "Kim '23",
      "citation_count": 142
    }
  ],
  "edges": [
    {
      "source": "paper_001",
      "target": "paper_005",
      "weight": 1.0
    }
  ]
}
```

---

#### `POST /v1/maps/{map_id}/refresh`

Re-run retrieval for new papers. Returns a new job.

**Response (202):**
```json
{
  "job_id": "job_xyz789",
  "status": "processing"
}
```

---

#### `GET /v1/maps/{map_id}/graph`

Retrieve the citation network with precomputed server-side force-directed layout.

**Response (200):**
```json
{
  "nodes": [
    {
      "paper_id": "paper_001",
      "x": 420.5,
      "y": 380.2,
      "size": 18.4,
      "year": 2021,
      "in_map": true,
      "label": "Smith+21",
      "citation_count": 120
    }
  ],
  "edges": [
    {
      "source": "paper_001",
      "target": "foundational_finfet_pioneer",
      "weight": 1.0
    }
  ]
}
```

---

#### `POST /v1/maps/{map_id}/export`

Export a map in the specified format (`md`, `bibtex`, `csv`).

**Request:**
```json
{
  "format": "md"
}
```

Supported formats: `md`, `bibtex`, `csv`.

**Response:**
```json
{
  "download_url": "/v1/maps/map_def456/download?format=md",
  "format": "md",
  "expires_at": "2025-01-16T10:30:00Z"
}
```

---

#### `GET /v1/maps/{map_id}/download?format={format}`

Download the exported research notebook directly as a file attachment (Content-Disposition: attachment).

---

### Jobs

#### `GET /v1/jobs/{job_id}/events`

Server-Sent Events stream for job progress.

**Event types:**

```
event: stage
data: {"stage": "expand_query", "stage_number": 1, "total_stages": 8}

event: log
data: {"message": "retrieved 187 candidates", "timestamp": "2025-01-15T10:30:12Z"}

event: paper_selected
data: {"paper_id": "paper_001", "title": "Low-Leakage ...", "year": 2023, "venue": "IEEE TED", "relevance_score": 0.92}

event: done
data: {"map_id": "map_def456", "total_papers": 25, "duration_seconds": 142}

event: error
data: {"code": "source_timeout", "message": "Semantic Scholar did not respond. Results use OpenAlex only.", "recoverable": true}
```

---

### Papers

#### `GET /v1/papers/{paper_id}`

Retrieve a single paper with its extraction and evidence.

**Response:**
```json
{
  "id": "paper_001",
  "doi": "10.1109/TED.2023.1234567",
  "title": "A 6T FinFET SRAM with Sub-10pW Standby Leakage",
  "authors": [
    {"name": "J. Kim", "affiliation": "KAIST"}
  ],
  "year": 2023,
  "venue": "IEEE Transactions on Electron Devices",
  "citation_count": 142,
  "abstract": "This paper presents ...",
  "oa_pdf_url": "https://arxiv.org/pdf/2301.12345.pdf",
  "has_full_text": true,
  "relevance_score": 0.92,
  "relevance_reason": "Directly addresses standby leakage in FinFET 6T SRAM with sub-10nm results.",
  "short_label": "Kim '23",
  "extraction": {
    "paper_id": "paper_001",
    "problem": {
      "text": "Standby leakage power in 6T SRAM cells at sub-7nm FinFET nodes",
      "evidence": {
        "quote": "Standby leakage has become the dominant power component in modern SRAM arrays at sub-7nm technology nodes",
        "section": "Introduction",
        "page": 1
      }
    },
    "method": {
      "text": "Dual-threshold voltage assignment with power gating",
      "evidence": { ... }
    },
    "technology": {
      "node_nm": 5,
      "device": "FinFET",
      "cell_type": "6T",
      "evidence": { ... }
    },
    "tools": [
      {
        "name": "Cadence Spectre",
        "category": "simulator",
        "evidence": { ... }
      }
    ],
    "datasets": [],
    "metrics": [
      {
        "name": "standby_leakage",
        "value": 8.7,
        "unit": "pW/cell",
        "normalized_value": 8.7e-12,
        "normalized_unit": "W/cell",
        "conditions": {
          "vdd": 0.7,
          "temp_c": 25,
          "corner": "TT"
        },
        "evidence": {
          "quote": "The proposed cell achieves 8.7 pW/cell standby leakage at 0.7V VDD and 25°C",
          "section": "Results",
          "page": 7
        },
        "confidence": "HIGH"
      }
    ],
    "findings": [
      {
        "id": "finding_001",
        "text": "Dual-Vt assignment reduces leakage by 62% compared to single-Vt baseline",
        "theme": "leakage_reduction",
        "paper_ids": ["paper_001"],
        "evidence": {
          "quote": "Our dual-Vt scheme demonstrates a 62% reduction in total cell leakage current compared to the conventional single-Vt 6T cell",
          "section": "Results",
          "page": 7
        },
        "confidence": "HIGH"
      }
    ],
    "limitations": [
      {
        "text": "Only validated at TT corner; no worst-case SS analysis",
        "evidence": { ... },
        "confidence": "HIGH"
      }
    ]
  }
}
```

---

### Library

#### `GET /v1/library/maps`

List saved maps for the current user, most recent first. Every map the user builds (search or upload), or opens from `POST /v1/maps` as a cached topic, is added automatically; removing one only takes it out of the library.

**Response:**
```json
{
  "maps": [
    {
      "id": "map_def456",
      "topic": "Low-leakage SRAM using FinFET",
      "created_at": "2025-01-15T10:30:00Z",
      "updated_at": "2025-01-15T10:35:00Z",
      "paper_count": 25,
      "saved_at": "2025-01-15T11:00:00Z"
    }
  ]
}
```

#### `POST /v1/library/maps/{map_id}`

Save a map to the user's library. Returns 201.

#### `DELETE /v1/library/maps/{map_id}`

Remove a map from the user's library. Returns 204.

#### `GET /v1/library/papers`

List saved papers.

#### `POST /v1/library/papers/{paper_id}`

Save a paper. Returns 201.

#### `DELETE /v1/library/papers/{paper_id}`

Remove a saved paper. Returns 204.

---

## Data Model Reference

### ResearchMap

| Field              | Type            | Description                                  |
|--------------------|-----------------|----------------------------------------------|
| `id`               | `string`        | Unique map identifier                        |
| `topic`            | `string`        | Original user-entered topic                  |
| `normalized_topic` | `string`        | Lowercase, stripped punctuation               |
| `filters`          | `MapFilters`    | Applied search filters                       |
| `created_at`       | `datetime`      | When the map was first created               |
| `updated_at`       | `datetime`      | Last update (refresh or initial completion)   |
| `stats`            | `MapStats`      | Summary statistics                           |
| `synthesis`        | `Synthesis`     | Overview paragraph with citation references  |
| `papers`           | `Paper[]`       | All papers in the map                        |
| `findings`         | `Finding[]`     | Cross-paper findings grouped by theme        |
| `gaps`             | `Gap[]`         | Identified research gaps                     |
| `contradictions`   | `Contradiction[]` | Conflicting results across papers          |
| `experiments`      | `Experiment[]`  | Suggested experiments to address gaps        |
| `tools`            | `ToolEntry[]`   | Tools, datasets, and resources mentioned     |

### MapFilters

| Field              | Type       | Default | Description                       |
|--------------------|------------|---------|-----------------------------------|
| `year_min`         | `int?`     | `null`  | Earliest publication year         |
| `year_max`         | `int?`     | `null`  | Latest publication year           |
| `fields`           | `string[]` | `[]`    | Fields of study to filter by      |
| `min_citations`    | `int`      | `0`     | Minimum citation count            |
| `open_access_only` | `bool`     | `false` | Only include open-access papers   |
| `max_papers`       | `int`      | `25`    | Maximum papers to include (20/30/50) |

### Paper

| Field              | Type         | Description                              |
|--------------------|--------------|------------------------------------------|
| `id`               | `string`     | Internal paper identifier                |
| `doi`              | `string?`    | DOI if available                         |
| `title`            | `string`     | Paper title                              |
| `authors`          | `Author[]`   | List of authors                          |
| `year`             | `int`        | Publication year                         |
| `venue`            | `string`     | Journal or conference name               |
| `citation_count`   | `int`        | Total citation count                     |
| `abstract`         | `string`     | Paper abstract                           |
| `oa_pdf_url`       | `string?`    | Open-access PDF URL if available         |
| `has_full_text`    | `bool`       | Whether full text was successfully parsed|
| `relevance_score`  | `float`      | 0.0–1.0 relevance to the topic          |
| `relevance_reason` | `string`     | One-sentence reason for inclusion        |
| `short_label`      | `string`     | E.g. "Kim '23"                           |

### PaperExtraction

| Field         | Type              | Description                           |
|---------------|-------------------|---------------------------------------|
| `paper_id`    | `string`          | Reference to parent paper             |
| `problem`     | `ExtractedField`  | Problem statement + evidence          |
| `method`      | `ExtractedField`  | Method description + evidence         |
| `technology`  | `Technology`      | Node, device, cell type + evidence    |
| `tools`       | `ToolRef[]`       | Tools used + evidence                 |
| `datasets`    | `DatasetRef[]`    | Datasets used + evidence              |
| `metrics`     | `Metric[]`        | Extracted metrics with conditions     |
| `findings`    | `Finding[]`       | Key findings with evidence            |
| `limitations` | `Limitation[]`    | Limitations with evidence             |

### Evidence

| Field     | Type     | Description                          |
|-----------|----------|--------------------------------------|
| `id`      | `string` | Evidence identifier                  |
| `paper_id`| `string` | Source paper                         |
| `quote`   | `string` | Verbatim quoted passage              |
| `section` | `string` | Section name (e.g. "Results")        |
| `page`    | `int?`   | Page number if available             |

### Metric

| Field              | Type       | Description                            |
|--------------------|------------|----------------------------------------|
| `name`             | `string`   | Metric name (e.g. "standby_leakage")   |
| `value`            | `float`    | Numeric value                          |
| `unit`             | `string`   | Unit as written (e.g. "pW/cell")       |
| `normalized_value` | `float`    | SI-normalized value                    |
| `normalized_unit`  | `string`   | SI unit (e.g. "W/cell")               |
| `conditions`       | `object`   | Experimental conditions (vdd, temp, etc.)|
| `evidence`         | `Evidence` | Source quote                           |
| `confidence`       | `string`   | `HIGH`, `MED`, or `LOW`               |

### Finding

| Field        | Type       | Description                           |
|--------------|------------|---------------------------------------|
| `id`         | `string`   | Finding identifier                    |
| `text`       | `string`   | Finding statement                     |
| `theme`      | `string`   | Thematic grouping                     |
| `paper_ids`  | `string[]` | Supporting papers                     |
| `evidence`   | `Evidence` | Source quote                          |
| `confidence` | `string`   | `HIGH`, `MED`, or `LOW`              |

### Gap

| Field                  | Type       | Description                     |
|------------------------|------------|---------------------------------|
| `id`                   | `string`   | Gap identifier (e.g. "G1")     |
| `statement`            | `string`   | Gap description                 |
| `pattern`              | `string`   | Quantified pattern (e.g. "0 of 24 papers...")|
| `supporting_paper_ids` | `string[]` | Papers that reveal this gap     |
| `why_it_matters`       | `string`   | Significance explanation        |
| `confidence`           | `string`   | `HIGH`, `MED`, or `LOW`        |

### Contradiction

| Field          | Type                 | Description                    |
|----------------|----------------------|--------------------------------|
| `id`           | `string`             | Contradiction identifier       |
| `metric`       | `string`             | Metric being compared          |
| `entries`      | `ContradictionEntry[]`| Side-by-side values           |
| `likely_reason` | `string`            | Explanation for disagreement   |
| `confidence`   | `string`             | `HIGH`, `MED`, or `LOW`       |

### ContradictionEntry

| Field        | Type     | Description               |
|--------------|----------|---------------------------|
| `paper_id`   | `string` | Source paper               |
| `value`      | `float`  | Metric value               |
| `unit`       | `string` | Unit                       |
| `conditions` | `object` | Experimental conditions    |

### GraphNode

| Field           | Type     | Description                    |
|-----------------|----------|--------------------------------|
| `paper_id`      | `string` | Paper reference                |
| `x`             | `float`  | X position (layout)            |
| `y`             | `float`  | Y position (layout)            |
| `size`          | `float`  | Node size (based on citations) |
| `year`          | `int`    | Publication year               |
| `in_map`        | `bool`   | Whether paper is in the map    |
| `label`         | `string` | Short label (e.g. "Kim '23")   |
| `citation_count`| `int`    | Citation count                 |

### GraphEdge

| Field    | Type     | Description            |
|----------|----------|------------------------|
| `source` | `string` | Source paper ID         |
| `target` | `string` | Target paper ID         |
| `weight` | `float`  | Edge weight (default 1.0)|

### Experiment

| Field            | Type       | Description                      |
|------------------|------------|----------------------------------|
| `id`             | `string`   | Experiment identifier            |
| `gap_id`         | `string`   | Gap this experiment addresses    |
| `hypothesis`     | `string`   | Research hypothesis              |
| `setup`          | `string`   | Experimental setup description   |
| `tools`          | `string[]` | Required tools                   |
| `variables`      | `string[]` | Key variables                    |
| `expected_result`| `string`   | Expected outcome                 |
| `difficulty`     | `string`   | `LOW`, `MED`, or `HIGH`         |
| `paper_ids`      | `string[]` | Related papers                   |

### Job

| Field      | Type       | Description                   |
|------------|------------|-------------------------------|
| `id`       | `string`   | Job identifier                |
| `map_id`   | `string`   | Associated map                |
| `status`   | `string`   | `queued`, `processing`, `done`, `error` |
| `stage`    | `string?`  | Current pipeline stage name   |
| `progress` | `float`    | 0.0–1.0 progress              |
| `log`      | `LogEntry[]`| Timestamped log messages     |
| `created_at`| `datetime` | Job creation time             |

### LogEntry

| Field       | Type       | Description                  |
|-------------|------------|------------------------------|
| `timestamp` | `datetime` | When the log was emitted     |
| `message`   | `string`   | Human-readable log line      |
