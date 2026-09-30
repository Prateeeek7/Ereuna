# Google Play Store Listing — Ereuna

## Metadata

- **Application Name**: EREUNA
- **Tagline**: Research, on your radar.
- **Package Name**: `com.researchradar`
- **Category**: Education / Tools / Reference
- **Content Rating**: Everyone

---

## Short Description (78 / 80 characters limit)
Evidence-backed research maps, citation graphs, and metric comparisons.

---

## Full Description (Sober Scientific / Methods Section Voice)

Ereuna converts academic research topics into structured literature maps. Rather than generating ungrounded conversational summaries, Ereuna operates as an automated systematic literature survey engine. Every extracted finding, gap, and comparison metric is tied directly to a verbatim quoted passage from peer-reviewed literature.

### 1. Retrieval & Deduplication
Ereuna queries multiple open academic bibliographic APIs simultaneously:
- **OpenAlex**: Global catalog of scholarly works, citations, and institutions.
- **Semantic Scholar Graph API**: Citation influence contexts and semantic embeddings.
- **arXiv.org**: Open-access preprints in physics, mathematics, computer science, and engineering.
- **Crossref**: Official DOI registration agency metadata.
- **Unpaywall**: Automated resolution of open-access full-text PDFs.

Records are normalized, canonicalized by DOI / title hash, deduplicated, and ranked using multi-factor relevance scoring (query-abstract BM25 overlap, publication recency, citation count, and full-text availability).

### 2. Evidence-Backed Extraction & Verification
- For papers with accessible full text, Ereuna analyzes complete document sections (Methodology, Results, Discussion).
- For paywalled works, structured analysis is extracted from peer-reviewed abstracts.
- Every extracted claim, experimental metric, and design technique includes an exact, verbatim quote from the source document.
- Claims without verifiable textual grounding are rejected by the extraction pipeline.

### 3. Cross-Paper Comparison Matrix
- Automatically generates unified comparison tables aligning key experimental parameters (e.g., technology node, operating voltage, leakage current, latency, clock frequency, dataset, error rate).
- Displays extracted numeric values alongside author-reported units and measurement conditions.
- Interactive column picker to customize displayed metrics.

### 4. Contradiction Detection & Physical Explanations
- Identifies conflicting claims across papers on identical benchmark metrics.
- Formulates hypothesis for divergence based on differing experimental parameters (e.g., ambient temperature, power supply voltage, process node variation).

### 5. Quantified Research Gaps & Experiment Formulator
- Detects unaddressed problem areas across the retrieved corpus.
- Quantifies coverage explicitly (e.g., "0 of 25 papers report temperature variation above 85°C").
- Formulates concrete, actionable experiment protocols designed to close identified gaps, including independent variables, recommended test benches, and difficulty ratings.

### 6. 2D Citation Network Visualizer
- Server-side Fruchterman-Reingold force-directed graph layout computed over cross-paper citation links.
- Interactive canvas with pinch-to-zoom (0.35x to 4.0x), two-dimensional pan, and year-gradient node coloring.
- Tap hit-testing reveals paper metadata, citation count, and direct navigation to paper details.

### 7. Multi-Format Scientific Export
- **Markdown (.md)**: Complete research synthesis, evidence quotes, findings, gaps, and formatted bibliographies ready for Obsidian, Notion, or lab notebooks.
- **BibTeX (.bib)**: Clean, standardized citation records ready for LaTeX, Overleaf, Zotero, or Mendeley.
- **Spreadsheet (.csv)**: Tabular export of paper parameters for Pandas, Excel, or Google Sheets.

### 8. Archival Field Notebook Aesthetic
- Custom typography: Newsreader serif for long-form synthesis and IBM Plex Mono for scientific metrics and data tables.
- Warm paper (`#F6F4EF`) and dark slate (`#141311`) archival color schemes.
- Zero AI conversational artifacts: no chatbots, no synthetic personae, no ungrounded extrapolations.
- Offline reading: recently opened research maps are cached on the device.

---

## Data Privacy & Integrity Statement
- An account (email and password) is required. The email is used only to sign in.
- Research topics, and PDFs you choose to upload, are sent over TLS to the Ereuna server to build maps.
- Uploaded PDFs are read to build the map and then discarded; maps built from them are visible only to your account.
- No user data is sold, monetized, or shared with third-party advertising networks.
- Saved maps and papers are stored with your account; recently opened maps are also cached on the device.
