"""Stage 5: Extract — structured extraction per paper with evidence verification.

Uses LLM to extract structured information from each paper's text, with a heuristic
fallback when no LLM is configured. Every extracted item carries a verbatim evidence
quote that is verified against the source text.
"""

import asyncio
import logging
import re
import uuid
from typing import Callable, Optional

from pydantic import BaseModel, Field

from app.clients.llm import LlmClient
from app.config import settings
from app.models.paper import (
    DatasetRef,
    Evidence,
    ExtractedField,
    Finding,
    Limitation,
    Metric,
    MetricConditions,
    Paper,
    PaperExtraction,
    Technology,
    ToolRef,
    VerificationReport,
)
from app.pipeline.evidence import verify_and_filter
from app.pipeline.metric_names import structure_metric
from app.pipeline.units import canonical_unit, fold_scientific, normalize_metric

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────
# Pydantic schemas for LLM structured extraction
# ──────────────────────────────────────────────────────────


class LlmEvidence(BaseModel):
    """Evidence quote from the paper text."""
    quote: str = Field(description="Exact verbatim quote from the paper text")
    section: str = Field(default="", description="Section heading where the quote appears")


class LlmExtractedField(BaseModel):
    """An extracted text field with evidence."""
    text: str = Field(description="The extracted information")
    evidence: LlmEvidence


class LlmMetricConditions(BaseModel):
    """Stated test conditions for a metric."""
    temp_c: Optional[float] = Field(default=None, description="Temperature in °C, only if stated")
    vdd: Optional[float] = Field(default=None, description="Supply or applied voltage in volts, only if stated")
    other: Optional[str] = Field(default=None, description="Other stated test conditions in the paper's words (e.g. 'after 20 cycles at 0.1 C', 'at 1 mA cm-2', 'on ImageNet'), only if stated")


class LlmMetric(BaseModel):
    """An extracted numeric result."""
    name: str = Field(description="The measured quantity only, 1-4 words in the field's standard terms (e.g. ionic conductivity, capacity retention, critical current density, top-1 accuracy, leakage power). Never put the sample, material or test condition in the name.")
    subject: str = Field(default="", description="What the value was measured on when the paper reports several: the material, sample, cell, model or variant (e.g. 'LiF-coated LPS', 'PEO/LiTFSI', '8T cell'). Empty for the paper's single main system.")
    value: float = Field(description="The number as a plain decimal, with scientific notation expanded (7.4 × 10−4 -> 0.00074)")
    unit: str = Field(description="Unit as written, without any power of ten (e.g. S cm−1, mAh g−1, %, ns)")
    conditions: LlmMetricConditions = Field(default_factory=LlmMetricConditions)
    evidence: LlmEvidence


class LlmFinding(BaseModel):
    """A key finding from the paper."""
    text: str = Field(description="The finding statement")
    theme: str = Field(default="", description="Category/theme of the finding")
    evidence: LlmEvidence


class LlmLimitation(BaseModel):
    """A limitation identified in the paper."""
    text: str = Field(description="The limitation")
    evidence: LlmEvidence


class LlmTool(BaseModel):
    """A tool or simulator referenced in the paper."""
    name: str = Field(description="Tool/simulator name")
    category: str = Field(default="", description="Category: simulator, pdk, hardware, eda")
    evidence: LlmEvidence


class LlmDataset(BaseModel):
    """A dataset or benchmark referenced in the paper."""
    name: str = Field(description="Dataset/benchmark name")
    category: str = Field(default="", description="Category: dataset, benchmark")
    evidence: LlmEvidence


class LlmTechnology(BaseModel):
    """Technology / platform details from the paper (only when the paper states them)."""
    node_nm: Optional[int] = Field(default=None, description="Semiconductor process node in nanometers, only if stated")
    device: Optional[str] = Field(default=None, description="Main device, material, model architecture or platform studied, only if stated")
    cell_type: Optional[str] = Field(default=None, description="Specific variant/configuration (e.g. 8T cell, 7B-parameter model), only if stated")
    evidence: LlmEvidence


class LlmExtractionResponse(BaseModel):
    """Complete structured extraction from a single paper."""
    problem: Optional[LlmExtractedField] = Field(default=None, description="Research problem being addressed")
    method: Optional[LlmExtractedField] = Field(default=None, description="Methodology or approach used")
    technology: Optional[LlmTechnology] = Field(default=None, description="Technology details")
    tools: list[LlmTool] = Field(default_factory=list, description="Tools and simulators used")
    datasets: list[LlmDataset] = Field(default_factory=list, description="Datasets and benchmarks used")
    metrics: list[LlmMetric] = Field(default_factory=list, description="Quantitative results and measurements")
    findings: list[LlmFinding] = Field(default_factory=list, description="Key findings (2-4 per paper)")
    limitations: list[LlmLimitation] = Field(default_factory=list, description="Limitations of the work")


class LlmAbstractResult(BaseModel):
    """Results from one abstract in a batch."""
    paper: str = Field(description="The paper's key from the list, e.g. P2")
    metrics: list[LlmMetric] = Field(default_factory=list, description="Quantitative results the abstract states")
    findings: list[LlmFinding] = Field(default_factory=list, description="1-3 specific result statements")
    limitations: list[LlmLimitation] = Field(default_factory=list, description="Limitations the abstract itself states")


class LlmAbstractBatch(BaseModel):
    """Structured results for several abstracts at once."""
    papers: list[LlmAbstractResult] = Field(default_factory=list)


# ──────────────────────────────────────────────────────────
# Source text preparation
# ──────────────────────────────────────────────────────────

_REFERENCES_HEADING_RE = re.compile(
    r"(?:^|\n)\s*(?:\d+\.?\s*)?(?:references|bibliography|literature cited|works cited)\s*(?:\n|$)",
    re.IGNORECASE,
)


def _source_text(paper: Paper) -> str:
    """Full parsed text when available, otherwise the abstract."""
    if paper.sections and paper.sections.full_text:
        return paper.sections.full_text
    return paper.abstract or ""


def strip_references(text: str) -> str:
    """Drop the bibliography so cited titles are not mistaken for this paper's claims.

    Only cuts at a References heading found in the second half of the document.
    """
    if not text:
        return ""
    matches = list(_REFERENCES_HEADING_RE.finditer(text))
    for m in reversed(matches):
        if m.start() > len(text) * 0.5:
            return text[: m.start()]
    return text


def _prompt_text(paper: Paper, max_chars: int) -> str:
    """Body text for the LLM: references removed, truncated to max_chars."""
    text = strip_references(_source_text(paper))
    if paper.abstract and paper.abstract[:80] not in text:
        text = f"Abstract\n{paper.abstract}\n\n{text}"
    if len(text) > max_chars:
        text = text[:max_chars] + "\n\n[TEXT TRUNCATED]"
    return text


# ──────────────────────────────────────────────────────────
# Prompt construction
# ──────────────────────────────────────────────────────────


def _build_extraction_prompt(paper: Paper, max_chars: int = 25000) -> str:
    """Build the extraction prompt for a single paper."""
    source_text = _prompt_text(paper, max_chars)

    return f"""Analyze this academic paper and extract structured information.

PAPER METADATA:
- Title: {paper.title}
- Authors: {', '.join(a.name for a in paper.authors[:5])}
- Year: {paper.year}
- Venue: {paper.venue}

PAPER TEXT:
{source_text}

INSTRUCTIONS:
1. Extract the problem being addressed and the methodology.
2. Extract technology/platform details only if the paper states them; otherwise omit "technology".
3. Extract 2-4 key findings. Each finding must be a specific, informative statement of a result.
4. Extract the paper's own quantitative results. For each give: name = the measured quantity only
   ("ionic conductivity", "capacity retention"), used identically for the same quantity; subject = the
   material, sample or variant it was measured on; conditions = the stated test conditions. Do not
   extract numbers the paper cites from other work, and do not extract years, section or figure numbers.
5. Extract tools/software/instruments and datasets/benchmarks the authors actually used.
6. Extract limitations the authors acknowledge.

CRITICAL RULES:
- Every item MUST include a verbatim quote from the paper text above as evidence.
- The quote must be an EXACT substring of the paper text — do not paraphrase or modify it.
- For metrics, the numeric value must appear in the quote exactly as reported.
- Keep quotes concise (1-3 sentences) but complete enough to support the extracted item.
- If you cannot find a direct quote for an item, DO NOT include that item.
- Use empty lists or null when the paper does not contain something. Never guess.
"""


# Abstract-only papers are sent a few at a time: an abstract is short, and one
# request per abstract would spend the free tiers' per-minute request limits.
ABSTRACT_BATCH_SIZE = 3
_ABSTRACT_CHARS = 2500


def _build_abstract_batch_prompt(keyed: list[tuple[str, Paper]], max_chars: int = 20000) -> str:
    per_paper = max(600, min(_ABSTRACT_CHARS, max_chars // max(1, len(keyed)) - 200))
    blocks = []
    for key, paper in keyed:
        abstract = " ".join((paper.abstract or "").split())[:per_paper]
        blocks.append(f"[{key}] {paper.title} ({paper.year})\nABSTRACT: {abstract}")
    listing = "\n\n".join(blocks)
    return f"""Extract structured results from each of these paper abstracts.

{listing}

For every paper return one entry with its key ([P1] -> "P1") and:
- metrics: the paper's own quantitative results. name = the measured quantity only ("ionic conductivity",
  "capacity retention"), worded the same way for the same quantity in every paper; subject = the material,
  sample or variant measured; conditions = the stated test conditions. Skip numbers cited from other work.
- findings: 1-3 specific result statements.
- limitations: only limitations the abstract itself states.

CRITICAL RULES:
- Every evidence quote must be an EXACT substring of that same paper's abstract. Never quote another abstract.
- For metrics, the number must appear in the quote as reported.
- If an abstract states no quantitative result, return an empty metrics list for it. Never guess.
"""


# ──────────────────────────────────────────────────────────
# LLM response → internal model conversion
# ──────────────────────────────────────────────────────────


def _to_evidence(paper_id: str, llm_ev: LlmEvidence) -> Evidence:
    """Convert LLM evidence to internal Evidence model."""
    return Evidence(
        id=f"ev_{uuid.uuid4().hex[:8]}",
        paper_id=paper_id,
        quote=llm_ev.quote,
        section=llm_ev.section,
    )


def _llm_to_extraction(paper_id: str, llm_resp: LlmExtractionResponse) -> PaperExtraction:
    """Convert LLM extraction response to internal PaperExtraction model."""
    problem = None
    if llm_resp.problem:
        problem = ExtractedField(
            text=llm_resp.problem.text,
            evidence=_to_evidence(paper_id, llm_resp.problem.evidence),
        )

    method = None
    if llm_resp.method:
        method = ExtractedField(
            text=llm_resp.method.text,
            evidence=_to_evidence(paper_id, llm_resp.method.evidence),
        )

    technology = None
    t = llm_resp.technology
    if t and (t.node_nm is not None or t.device or t.cell_type):
        technology = Technology(
            node_nm=t.node_nm,
            device=t.device,
            cell_type=t.cell_type,
            evidence=_to_evidence(paper_id, t.evidence),
        )

    tools = [
        ToolRef(name=t.name.strip(), category=t.category, evidence=_to_evidence(paper_id, t.evidence))
        for t in llm_resp.tools
        if t.name.strip()
    ]

    datasets = [
        DatasetRef(name=d.name.strip(), category=d.category, evidence=_to_evidence(paper_id, d.evidence))
        for d in llm_resp.datasets
        if d.name.strip()
    ]

    metrics = []
    for m in llm_resp.metrics:
        value, unit = fold_scientific(m.value, m.unit)
        name, subject, other = structure_metric(m.name, m.subject, m.conditions.other)
        norm_value, norm_unit = normalize_metric(value, unit)
        metrics.append(
            Metric(
                name=name,
                value=value,
                unit=unit.strip(),
                subject=subject,
                normalized_value=norm_value,
                normalized_unit=norm_unit,
                conditions=MetricConditions(
                    vdd=m.conditions.vdd,
                    temp_c=m.conditions.temp_c,
                    other=other,
                ),
                evidence=_to_evidence(paper_id, m.evidence),
            )
        )

    findings = [
        Finding(
            id=f"f_{uuid.uuid4().hex[:8]}",
            text=f.text,
            theme=f.theme,
            paper_ids=[paper_id],
            evidence=_to_evidence(paper_id, f.evidence),
        )
        for f in llm_resp.findings
    ]

    limitations = [
        Limitation(text=l.text, evidence=_to_evidence(paper_id, l.evidence))
        for l in llm_resp.limitations
    ]

    return PaperExtraction(
        paper_id=paper_id,
        problem=problem,
        method=method,
        technology=technology,
        tools=_unique(tools, lambda t: t.name.lower()),
        datasets=_unique(datasets, lambda d: d.name.lower()),
        metrics=_unique(metrics, lambda m: (m.name.lower(), m.subject.lower(), m.value, m.unit)),
        findings=_unique(findings, lambda f: " ".join(f.text.lower().split())),
        limitations=_unique(limitations, lambda l: " ".join(l.text.lower().split())),
    )


def _unique(items: list, key: Callable) -> list:
    """Drop repeated items (models sometimes emit the same entry twice)."""
    seen: set = set()
    out = []
    for item in items:
        k = key(item)
        if k not in seen:
            seen.add(k)
            out.append(item)
    return out


# ──────────────────────────────────────────────────────────
# Heuristic fallback extraction (no LLM required)
# ──────────────────────────────────────────────────────────
#
# The heuristics are deliberately conservative: every item is a verbatim
# sentence from the paper, and metrics are only taken from explicit
# "<quantity> of <number><unit>" / "<number><unit> <quantity>" /
# "improves X by N%" phrasings so figure numbers, years and citations are
# not mistaken for results.

_NUM = r"(?P<val>\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"
# Optional power of ten after the number: "7.4 × 10−4", "7.4x10^-4".
_SCI = r"(?:\s?[×x]\s?10\s?\^?\s?\(?(?P<exp>[-−–]?\s?\d{1,2})\)?)?"
_APPROX = r"(?:(?:about|approximately|around|roughly|nearly|over|up to|more than|less than|only|just)\s+|~\s*)?"

# Measurement units: SI-prefixed physical units, compound units, and common
# computing/ML units. Case-sensitive on purpose ("mW" vs "MW").
_UNIT = (
    r"(?P<unit>"
    r"%|×|"
    r"(?:[fpnuµμmkMGT]?(?:Wh|Ah|W|A|V|J|Hz|F|eV|Ω|S|Pa|s|m|g|mol|L|B|b|FLOPs?|OPS))"
    r"(?:\s?/\s?(?:[fpnuµμmkMGT]?(?:W|A|V|J|Hz|F|Wh|s|m|g|kg|cm²|cm2|cm|mm²|mm2|m²|m2|bit|cell|token|tokens|image|images|sample|samples|cycle|L|mol|h)))?"
    # Product units with exponents: "S cm−1", "mAh g−1", "mA cm−2", "Ω cm2".
    r"(?:\s?[·]?\s?(?:[cfpnuµμmk]?(?:g|m|L|mol|s|h|V|A|W))(?:[−–-]?[123]|⁻?[¹²³]))*"
    r"|°C|K|dB|dBm|ppm|ppb|tokens/s|tok/s|it/s|fps|FPS"
    r")"
)

# Longest phrases first, so "critical current density" is matched before "density".
_QUANTITY_WORDS = (
    r"short-circuit current density|power conversion efficiency|external quantum efficiency|"
    r"charge transfer resistance|critical current density|electrical conductivity|"
    r"interfacial resistance|coulombic efficiency|open circuit voltage|thermal conductivity|"
    r"faradaic efficiency|adsorption capacity|energy consumption|capacity retention|"
    r"discharge capacity|quantum efficiency|turnover frequency|ionic conductivity|limit of detection|"
    r"energy efficiency|power consumption|specific capacity|activation energy|tensile strength|"
    r"binding affinity|word error rate|current density|young's modulus|detection limit|top-1 accuracy|"
    r"top-5 accuracy|areal capacity|energy density|power density|overpotential|noise margin|"
    r"conductivity|surface area|exact match|write delay|fill factor|tafel slope|selectivity|"
    r"sensitivity|specificity|pore volume|temperature|perplexity|error rate|throughput|read delay|"
    r"efficiency|resistance|precision|bandwidth|band ?gap|stability|half-life|accuracy|win rate|"
    r"speed-up|capacity|mobility|lifetime|hardness|latency|speedup|leakage(?: current| power)?|"
    r"voltage|current|density|recall|pass@1|memory(?: footprint| usage)?|energy|uptake|auroc|"
    r"rouge(?:-[l12])?|power|delay|yield|bleu|area|ic50|ec50|rmse|psnr|ssim|auc|mae|mse|fid|"
    r"f1(?:[- ]score)?"
)

# "<quantity> of 92.3%" / "accuracy reaches 0.91"
_METRIC_NAMED_RE = re.compile(
    rf"(?P<what>\b(?:{_QUANTITY_WORDS}))\s+(?:of|is|was|=|:|reaches|reached|achieves|achieved|attains|attained|to|at)\s+"
    rf"{_APPROX}{_NUM}{_SCI}\s?{_UNIT}?(?![\w/])",
    re.IGNORECASE,
)

# "8.3 pW/cell standby leakage" / "92.3% top-1 accuracy"
_METRIC_VALUE_FIRST_RE = re.compile(
    rf"{_APPROX}{_NUM}{_SCI}\s?{_UNIT}\s+(?P<what>(?:(?!(?:with|and|or|of|for|at|a|an|the|to|in|by|from|as|was|is)\s){{1}}[a-z][a-z\-]*\s){{0,2}}(?:{_QUANTITY_WORDS}))\b",
    re.IGNORECASE,
)

# "reduces latency by 35%" / "improves accuracy by 2.1 points"
_METRIC_CHANGE_RE = re.compile(
    r"(?P<verb>reduc\w*|improv\w*|increas\w*|decreas\w*|lower\w*|boost\w*|cut\w*|outperform\w*|accelerat\w*)\s+"
    r"(?:the\s+|its\s+|their\s+)?(?P<what>[a-z][a-z\-]+(?:\s[a-z][a-z\-]+){0,2})\s+by\s+"
    rf"{_APPROX}{_NUM}\s?(?P<unit>%|×|x|times|percentage points)",
    re.IGNORECASE,
)

# "3.2× faster" / "2x speedup"
_METRIC_SPEEDUP_RE = re.compile(
    rf"{_APPROX}{_NUM}\s?(?P<unit>×|x|times)\s+(?P<what>faster|speedup|speed-up|higher throughput|lower latency|"
    r"less memory|fewer parameters|smaller|larger|more efficient|improvement)",
    re.IGNORECASE,
)

# Software, instruments and platforms (case-sensitive, whole-word).
_KNOWN_TOOLS: dict[str, str] = {
    # EDA / device simulation
    "HSPICE": "simulator", "Spectre": "simulator", "Virtuoso": "eda", "Sentaurus": "simulator",
    "Silvaco": "simulator", "LTspice": "simulator", "ModelSim": "simulator", "Vivado": "eda",
    "Quartus": "eda", "Calibre": "eda", "BSIM-CMG": "model", "BSIM": "model", "COMSOL": "simulator",
    "Ansys": "simulator", "Keysight ADS": "simulator", "Cadence": "eda", "Synopsys": "eda",
    # General scientific computing
    "MATLAB": "software", "Simulink": "software", "Python": "software", "LabVIEW": "software",
    # Machine learning
    "PyTorch": "framework", "TensorFlow": "framework", "JAX": "framework", "Keras": "framework",
    "scikit-learn": "framework", "Hugging Face": "framework", "DeepSpeed": "framework",
    "Megatron-LM": "framework", "vLLM": "framework", "TensorRT": "framework", "ONNX": "framework",
    "CUDA": "platform", "Triton": "framework",
    # Chemistry / materials / biology
    "VASP": "simulator", "Quantum ESPRESSO": "simulator", "LAMMPS": "simulator", "GROMACS": "simulator",
    "Gaussian 09": "software", "Gaussian 16": "software", "ORCA": "software", "AlphaFold": "software",
    "Rosetta": "software", "PyMOL": "software", "BLAST": "software", "GATK": "software",
    "Seurat": "software", "Cell Ranger": "software",
}

_KNOWN_DATASETS: set[str] = {
    "ImageNet", "CIFAR-10", "CIFAR-100", "MNIST", "MS COCO", "COCO", "GLUE", "SuperGLUE", "SQuAD",
    "MMLU", "GSM8K", "HumanEval", "MBPP", "WikiText-103", "WikiText-2", "The Pile", "LibriSpeech",
    "HellaSwag", "TriviaQA", "Natural Questions", "BIG-bench", "MS MARCO", "LongBench",
    "PDB", "UniProt", "CASP14", "CASP15", "QM9", "Materials Project", "OC20",
    "METR-LA", "PEMS-BAY", "PeMSD4", "PeMSD8", "MIMIC-III", "MIMIC-IV", "UK Biobank", "TCGA",
}

_TOOL_RES = {name: re.compile(rf"(?<![\w-]){re.escape(name)}(?![\w-])") for name in _KNOWN_TOOLS}
_DATASET_RES = {name: re.compile(rf"(?<![\w-]){re.escape(name)}(?![\w-])") for name in _KNOWN_DATASETS}

_METHOD_CUE_RE = re.compile(
    r"\b(?:we (?:propose|present|introduce|develop|design|describe|build|report|demonstrate|investigate|study)|"
    r"(?:this|the present) (?:paper|work|study|article) (?:proposes|presents|introduces|develops|describes|reports|investigates)|"
    r"in this (?:paper|work|study|article),? we)\b",
    re.IGNORECASE,
)
_RESULT_CUE_RE = re.compile(
    r"\b(?:results? (?:show|demonstrate|indicate|reveal|suggest)|we (?:show|find|found|observe|demonstrate)|"
    r"achiev\w+|outperform\w*|improv\w+|reduc\w+|increas\w+|enabl\w+|yield\w*|leads? to)\b",
    re.IGNORECASE,
)
_LIMITATION_CUE_RE = re.compile(
    r"\b(?:(?:a|one|the|main|key|major|primary|another) limitation (?:of|is)|limitations? of (?:our|this)|"
    r"future work|left for future|beyond the scope|remains? (?:an )?open (?:question|problem|challenge)|"
    r"remains? unclear|(?:have|has) not (?:yet )?been (?:explored|investigated|studied|addressed|evaluated)|"
    r"(?:we|our \w+|this (?:work|study|paper|method|approach|model)) (?:does|do|did) not (?:account|consider|address|capture|generalize|evaluate|explore|support|handle)|"
    r"we (?:did|do) not|is limited to|are limited to|a drawback|one drawback|shortcoming)\b",
    re.IGNORECASE,
)
# Self-reference: the sentence talks about this paper's own work.
_SELF_REF_RE = re.compile(r"\b(?:we|our|us|this (?:work|study|paper|method|approach|model|analysis))\b", re.IGNORECASE)
# Sentences describing other papers (citations) or section headings are not the authors' own limitations.
_CITATION_RE = re.compile(r"\[\d+(?:[,–-]\s*\d+)*\]|\(\s*[A-Z][A-Za-z\-]+ et al\.?,? \d{4}|\bet al\.")
_HEADING_START_RE = re.compile(r"^(?:\d+(?:\.\d+)*\.?\s+)?(?:limitations?|conclusions?|discussion|future work)\b", re.IGNORECASE)


_LINEBREAK_HYPHEN_RE = re.compile(r"(\w)-\s*\n\s*(\w)")


def _sentences(text: str) -> list[str]:
    """Whitespace-normalized sentences (quotes remain verifiable after normalization).

    Words hyphenated across PDF line breaks are rejoined first, exactly as the
    evidence verifier normalizes the source text.
    """
    flat = _LINEBREAK_HYPHEN_RE.sub(r"\1\2", text or "")
    flat = re.sub(r"\s+", " ", flat).strip()
    if not flat:
        return []
    parts = re.split(r"(?<=[.!?])\s+(?=[A-Z0-9(\"“])", flat)
    return [p.strip() for p in parts if p.strip()]


def _strip_heading_prefix(sentence: str) -> str:
    return re.sub(r"^(?:abstract|summary)\s*[:.\-—–]?\s*", "", sentence, flags=re.IGNORECASE).strip()


def _sentence_containing(sentences: list[str], start_idx: int, offsets: list[int]) -> str:
    """Sentence that contains the character offset start_idx."""
    lo, hi = 0, len(offsets) - 1
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if offsets[mid] <= start_idx:
            lo = mid
        else:
            hi = mid - 1
    return sentences[lo] if sentences else ""


def _parse_number(raw: str, exponent: Optional[str] = None) -> Optional[float]:
    try:
        value = float(raw.replace(",", ""))
    except ValueError:
        return None
    if exponent:
        exp = exponent.replace(" ", "").replace("−", "-").replace("–", "-")
        value = float(f"{value * 10.0 ** int(exp):.12g}")
    return value


def _heuristic_metrics(paper_id: str, text: str) -> list[Metric]:
    sentences = _sentences(text)
    if not sentences:
        return []
    joined = " ".join(sentences)
    offsets: list[int] = []
    pos = 0
    for sent in sentences:
        offsets.append(pos)
        pos += len(sent) + 1

    metrics: list[Metric] = []
    seen: set[tuple[str, float, str]] = set()

    def add(name: str, value: float, unit: str, match_start: int) -> None:
        words = name.lower().split()
        while words and words[0] in ("of", "in", "the", "a", "an", "and", "with", "on", "for", "to"):
            words = words[1:]
        name = " ".join(words)
        if not name:
            return
        unit = canonical_unit(unit.strip())
        if unit in ("x", "times"):
            unit = "×"
        if unit == "percentage points":
            unit = "pp"
        # Bare numbers that look like years, or unitless values next to non-ratio names, are ambiguous.
        if not unit and (1900 <= value <= 2100 or value > 1000):
            return
        key = (name, value, unit)
        if key in seen:
            return
        seen.add(key)
        quote = _sentence_containing(sentences, match_start, offsets)
        if len(quote) > 600:
            return
        norm_value, norm_unit = normalize_metric(value, unit) if unit else (value, "")
        metrics.append(
            Metric(
                name=name,
                value=value,
                unit=unit,
                normalized_value=norm_value,
                normalized_unit=norm_unit,
                evidence=Evidence(id=f"ev_{uuid.uuid4().hex[:8]}", paper_id=paper_id, quote=quote),
            )
        )

    for m in _METRIC_NAMED_RE.finditer(joined):
        val = _parse_number(m.group("val"), m.group("exp"))
        if val is not None:
            add(m.group("what"), val, m.group("unit") or "", m.start())
    for m in _METRIC_VALUE_FIRST_RE.finditer(joined):
        val = _parse_number(m.group("val"), m.group("exp"))
        if val is not None:
            add(m.group("what"), val, m.group("unit"), m.start())
    for m in _METRIC_CHANGE_RE.finditer(joined):
        val = _parse_number(m.group("val"))
        if val is not None:
            verb = m.group("verb").lower()
            direction = "reduction" if verb.startswith(("reduc", "decreas", "lower", "cut")) else "improvement"
            add(f"{m.group('what')} {direction}", val, m.group("unit"), m.start())
    for m in _METRIC_SPEEDUP_RE.finditer(joined):
        val = _parse_number(m.group("val"))
        if val is not None:
            add(m.group("what"), val, m.group("unit"), m.start())

    return metrics


def _heuristic_extract(paper: Paper) -> PaperExtraction:
    """Conservative extraction using sentence cues and explicit measurement phrasings.

    Used when no LLM is configured. Every item quotes a sentence from the paper verbatim.
    """
    source_text = strip_references(_source_text(paper))
    if not source_text:
        return PaperExtraction(paper_id=paper.id, extracted_by="heuristic")

    abstract = paper.abstract or source_text[:2000]
    abstract_sents = [_strip_heading_prefix(s) for s in _sentences(abstract)]
    abstract_sents = [s for s in abstract_sents if len(s) >= 25]
    body_sents = _sentences(source_text)

    def ev(quote: str, section: str = "") -> Evidence:
        return Evidence(id=f"ev_{uuid.uuid4().hex[:8]}", paper_id=paper.id, quote=quote, section=section)

    abstract_section = "Abstract" if paper.abstract else ""

    # Method: first sentence that states what the authors did.
    method = None
    method_sentence = next((s for s in abstract_sents if _METHOD_CUE_RE.search(s)), None)
    if method_sentence:
        method = ExtractedField(text=method_sentence, evidence=ev(method_sentence, abstract_section))

    # Problem: the opening sentence of the abstract, when it is not the method sentence.
    problem = None
    if abstract_sents and abstract_sents[0] != method_sentence:
        problem = ExtractedField(text=abstract_sents[0], evidence=ev(abstract_sents[0], abstract_section))

    # Findings: result-oriented abstract sentences.
    findings: list[Finding] = []
    for sent in abstract_sents:
        if sent in (method_sentence, abstract_sents[0]) and len(abstract_sents) > 2:
            continue
        if _RESULT_CUE_RE.search(sent) and 40 <= len(sent) <= 450:
            findings.append(
                Finding(
                    id=f"f_{uuid.uuid4().hex[:8]}",
                    text=sent,
                    paper_ids=[paper.id],
                    evidence=ev(sent, abstract_section),
                )
            )
            if len(findings) >= 4:
                break

    # Limitations: sentences where authors state a limitation or open issue.
    limitations: list[Limitation] = []
    for sent in body_sents:
        if not (40 <= len(sent) <= 400) or not _LIMITATION_CUE_RE.search(sent):
            continue
        if _CITATION_RE.search(sent) or _HEADING_START_RE.match(sent) or sent.endswith("?"):
            continue
        if not _SELF_REF_RE.search(sent):
            continue
        limitations.append(Limitation(text=sent, evidence=ev(sent)))
        if len(limitations) >= 4:
            break

    # Tools and datasets: whole-word, case-sensitive name matches.
    tools: list[ToolRef] = []
    for name, category in _KNOWN_TOOLS.items():
        m = _TOOL_RES[name].search(source_text)
        if m:
            quote = _sentence_containing_span(source_text, m.start(), m.end())
            tools.append(ToolRef(name=name, category=category, evidence=ev(quote)))
    # Prefer the most specific name when one contains another (e.g. "BSIM-CMG" over "BSIM").
    names = {t.name for t in tools}
    tools = [t for t in tools if not any(t.name != o and t.name in o for o in names)]

    datasets: list[DatasetRef] = []
    for name in sorted(_KNOWN_DATASETS):
        m = _DATASET_RES[name].search(source_text)
        if m:
            quote = _sentence_containing_span(source_text, m.start(), m.end())
            datasets.append(DatasetRef(name=name, category="dataset", evidence=ev(quote)))
    dnames = {d.name for d in datasets}
    datasets = [d for d in datasets if not any(d.name != o and d.name in o for o in dnames)]

    return PaperExtraction(
        paper_id=paper.id,
        problem=problem,
        method=method,
        tools=tools,
        datasets=datasets,
        metrics=_heuristic_metrics(paper.id, source_text)[:12],
        findings=findings,
        limitations=limitations,
        extracted_by="heuristic",
    )


def _sentence_containing_span(text: str, start: int, end: int, max_len: int = 400) -> str:
    """The sentence around text[start:end], whitespace-normalized and capped in length."""
    left = max(text.rfind(". ", 0, start), text.rfind("\n\n", 0, start))
    left = 0 if left == -1 else left + 1
    right_candidates = [i for i in (text.find(". ", end), text.find("\n\n", end)) if i != -1]
    right = min(right_candidates) + 1 if right_candidates else len(text)
    raw = _LINEBREAK_HYPHEN_RE.sub(r"\1\2", text[left:right])
    snippet = re.sub(r"\s+", " ", raw).strip()
    if len(snippet) > max_len:
        center = start - left
        lo = max(0, center - max_len // 2)
        snippet = snippet[lo : lo + max_len].strip()
    return snippet


# ──────────────────────────────────────────────────────────
# Main extraction pipeline
# ──────────────────────────────────────────────────────────


def _confidence_for(paper: Paper, extracted_by: str) -> str:
    if extracted_by == "heuristic":
        return "MED" if paper.has_full_text else "LOW"
    return "HIGH" if paper.has_full_text else "MED"


async def extract_single_paper(
    paper: Paper,
    llm: Optional[LlmClient] = None,
    preset: Optional[PaperExtraction] = None,
) -> tuple[Paper, VerificationReport]:
    """Extract structured information from a single paper.

    `preset` is an extraction already made elsewhere (an abstract batch); it is
    verified like any other.

    Returns:
        Tuple of (paper_with_extraction, verification_report).
    """
    paper_id = paper.id
    source_text = _source_text(paper)

    if not source_text:
        return paper, VerificationReport(paper_id=paper_id)
    if preset is not None and paper.abstract and paper.abstract[:80] not in source_text:
        # Batched items quote the abstract; make sure verification can see it.
        source_text = f"{paper.abstract}\n\n{source_text}"

    extraction: PaperExtraction
    if preset is not None:
        extraction = preset
    elif llm and llm.is_configured():
        try:
            llm_resp = await llm.structured(
                lambda max_chars: _build_extraction_prompt(paper, max_chars=max_chars),
                LlmExtractionResponse,
            )
            extraction = _llm_to_extraction(paper_id, llm_resp)
            extraction.extracted_by = f"llm:{llm.last_model or llm.model}"
            logger.debug("[%s] LLM extraction: %d metrics, %d findings",
                         paper_id, len(extraction.metrics), len(extraction.findings))
        except Exception as e:
            logger.warning("[%s] LLM extraction failed, using heuristic: %s", paper_id, e)
            extraction = _heuristic_extract(paper)
    else:
        extraction = _heuristic_extract(paper)

    confidence = _confidence_for(paper, extraction.extracted_by)
    for metric in extraction.metrics:
        metric.confidence = confidence
    for finding in extraction.findings:
        finding.confidence = confidence
    for limitation in extraction.limitations:
        limitation.confidence = confidence

    # Evidence verification is CPU-bound (fuzzy matching); keep the event loop free.
    filtered, report = await asyncio.to_thread(verify_and_filter, extraction, source_text, paper.sections)

    return paper.model_copy(update={"extraction": filtered}), report


async def _extract_abstract_batch(batch: list[Paper], llm: LlmClient) -> dict[str, PaperExtraction]:
    """LLM results for several abstracts in one request, keyed by paper id.

    Problem, method, tools and datasets come from the text rules (they are single
    sentences or known names); the LLM adds metrics, findings and limitations.
    Papers the model skips are simply missing from the result.
    """
    keyed = [(f"P{i + 1}", paper) for i, paper in enumerate(batch)]
    by_key = dict(keyed)
    resp = await llm.structured(lambda max_chars: _build_abstract_batch_prompt(keyed, max_chars), LlmAbstractBatch)
    out: dict[str, PaperExtraction] = {}
    for item in resp.papers:
        paper = by_key.get(item.paper.strip().strip("[]").upper())
        if paper is None or paper.id in out:
            continue
        extraction = _llm_to_extraction(
            paper.id,
            LlmExtractionResponse(metrics=item.metrics, findings=item.findings, limitations=item.limitations),
        )
        rules = _heuristic_extract(paper)
        extraction.problem, extraction.method = rules.problem, rules.method
        extraction.tools, extraction.datasets = rules.tools, rules.datasets
        if not extraction.findings:
            extraction.findings = rules.findings
        extraction.extracted_by = f"llm:{llm.last_model or llm.model}"
        out[paper.id] = extraction
    return out


async def extract(
    papers: list[Paper],
    on_progress: Optional[Callable[[str], None]] = None,
) -> tuple[list[Paper], list[VerificationReport]]:
    """Extract structured information from each paper.

    Per paper extracts: problem, method, technology, tools, datasets,
    metrics (value, unit, conditions), findings, limitations.
    Every item carries an evidence quote that must be found in the paper text;
    items that fail verification are dropped.

    Returns:
        Tuple of (papers_with_extraction, verification_reports) in input order.
    """
    if not papers:
        return papers, []

    llm = LlmClient()
    use_llm = llm.is_configured()

    # Papers the user uploaded are always read individually; other full texts
    # are too, up to LLM_MAX_PAPERS in rank order. Abstract-only papers go to
    # the LLM a few at a time; the rest use text rules. Papers with no text at
    # all have nothing to extract.
    with_text = [p for p in papers if _source_text(p)]
    limit = settings.llm_max_papers if settings.llm_max_papers > 0 else len(papers)
    uploaded = [p for p in with_text if p.source == "upload"]
    found = [p for p in with_text if p.has_full_text and p.source != "upload"]
    individual = (uploaded + found[: max(0, limit - len(uploaded))]) if use_llm else []
    individual_ids = {p.id for p in individual}
    batched = [p for p in with_text if p.id not in individual_ids and (p.abstract or "").strip()] if use_llm else []
    batches = [batched[i : i + ABSTRACT_BATCH_SIZE] for i in range(0, len(batched), ABSTRACT_BATCH_SIZE)]
    rules_only = len(papers) - len(individual) - len(batched)

    if not use_llm:
        mode = "text rules (no LLM configured)"
    else:
        mode = (
            f"LLM ({llm.describe()}): {len(individual)} full texts read individually, "
            f"{len(batched)} abstracts in {len(batches)} batches"
            + (f", text rules for {rules_only}" if rules_only else "")
        )
    if on_progress:
        on_progress(f"Extracting structured data from {len(papers)} papers using {mode}...")

    done_count = 0

    def tick() -> None:
        nonlocal done_count
        done_count += 1
        if on_progress and (done_count % 5 == 0 or done_count == len(papers)):
            on_progress(f"Extracted {done_count}/{len(papers)} papers...")

    async def run_one(paper: Paper, llm_for_paper: Optional[LlmClient], preset: Optional[PaperExtraction] = None):
        try:
            result = await extract_single_paper(paper, llm_for_paper, preset=preset)
        except Exception as e:
            logger.warning("[%s] Extraction failed: %s", paper.id, e)
            result = (paper, VerificationReport(paper_id=paper.id))
        tick()
        return result

    async def run_batch(batch: list[Paper]) -> list[tuple[Paper, VerificationReport]]:
        try:
            presets = await _extract_abstract_batch(batch, llm)
        except Exception as e:
            logger.warning("Abstract batch failed, using text rules for %d papers: %s", len(batch), e)
            presets = {}
        return [await run_one(paper, None, presets.get(paper.id)) for paper in batch]

    async def run_all() -> list[tuple[Paper, VerificationReport]]:
        batch_ids = {p.id for p in batched}
        tasks = [run_one(p, llm if p.id in individual_ids else None) for p in papers if p.id not in batch_ids]
        tasks += [run_batch(b) for b in batches]
        by_id: dict[str, tuple[Paper, VerificationReport]] = {}
        for result in await asyncio.gather(*tasks):
            for paper, report in (result if isinstance(result, list) else [result]):
                by_id[paper.id] = (paper, report)
        return [by_id[p.id] for p in papers]

    try:
        # LlmClient enforces the process-wide concurrency limit.
        results = await run_all()
        processed_papers = [r[0] for r in results]
        reports = [r[1] for r in results]

        total_items = sum(r.total_items for r in reports)
        verified_items = sum(r.verified_items for r in reports)
        dropped_items = sum(r.dropped_items for r in reports)
        overall_rate = (verified_items / total_items * 100) if total_items > 0 else 0.0

        if on_progress:
            on_progress(
                f"Extraction complete: {verified_items}/{total_items} items "
                f"verified ({overall_rate:.0f}% pass rate). "
                f"{dropped_items} items dropped."
            )

        logger.info(
            "Extraction pipeline: %d papers, %d/%d items verified (%.1f%% pass rate)",
            len(papers), verified_items, total_items, overall_rate,
        )
        return processed_papers, reports

    finally:
        await llm.close()
