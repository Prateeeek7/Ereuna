"""Evidence verification — ensures extracted quotes exist in source text.

Implements whitespace/hyphenation/punctuation normalization and fuzzy matching
to verify that LLM-produced evidence quotes are real substrings of the paper text.
Drops unverifiable items and reports the drop rate.
"""

import logging
import re
import unicodedata
from difflib import SequenceMatcher
from typing import Optional

from app.models.paper import (
    Evidence,
    Finding,
    Limitation,
    Metric,
    PaperExtraction,
    PaperSections,
    ToolRef,
    DatasetRef,
    VerificationReport,
)

logger = logging.getLogger(__name__)


_NUMBER_RE = re.compile(r"\d+(?:[.,]\d+)*")


def _normalize_text(text: str) -> str:
    """Normalize text for comparison: whitespace, hyphens, quotes, unicode.

    - Collapse all whitespace runs to a single space
    - Remove soft hyphens and rejoin hyphenated line-breaks
    - Normalize typographic quotes to ASCII
    - Normalize unicode (NFKD)
    - Lowercase
    """
    if not text:
        return ""

    # Unicode normalization (NFKD decomposes characters)
    text = unicodedata.normalize("NFKD", text)

    # Remove soft hyphens (U+00AD)
    text = text.replace("\u00ad", "")

    # Rejoin hyphenated line-breaks: "word-\nword" -> "wordword"
    text = re.sub(r"(\w)-\s*\n\s*(\w)", r"\1\2", text)

    # Normalize typographic quotes to ASCII
    text = text.replace("\u2018", "'").replace("\u2019", "'")  # single quotes
    text = text.replace("\u201c", '"').replace("\u201d", '"')  # double quotes
    text = text.replace("\u2013", "-").replace("\u2014", "-")  # en/em dash
    text = text.replace("\u2015", "-")  # horizontal bar

    # Collapse whitespace
    text = re.sub(r"\s+", " ", text).strip()

    # Lowercase
    text = text.lower()

    return text


def verify_evidence(
    quote: str,
    source_text: str,
    fuzzy_threshold: float = 0.90,
    normalized_source: Optional[str] = None,
) -> bool:
    """Check whether a quote is a verifiable substring of the source text.

    Strategy:
    1. Exact normalized substring match
    2. If exact fails, sliding-window fuzzy match (SequenceMatcher) over the
       source text with a window sized to the quote length

    Args:
        quote: The extracted evidence quote to verify.
        source_text: The full paper text (concatenated sections or abstract).
        fuzzy_threshold: Minimum similarity ratio for fuzzy match (default 0.90).

    Returns:
        True if the quote can be verified in the source text.
    """
    if not quote or not source_text:
        return False

    norm_quote = _normalize_text(quote)
    norm_source = normalized_source if normalized_source is not None else _normalize_text(source_text)

    if not norm_quote or not norm_source:
        return False

    # 1. Exact substring match (normalized)
    if norm_quote in norm_source:
        return True

    # 2. Fuzzy sliding-window match
    # Only attempt if quote is reasonably short compared to source
    quote_len = len(norm_quote)
    source_len = len(norm_source)

    if quote_len < 10:
        # Very short quotes: require exact match
        return False

    if quote_len > source_len:
        return False

    # Anchor-based fuzzy match: locate short exact shingles of the quote in the
    # source (fast C-level str.find), then score only the aligned windows.
    # A quote with small edits (dropped word, OCR glitch) still shares exact
    # shingles with its true location; a fabricated quote shares none.
    shingle = 16 if quote_len >= 48 else max(8, quote_len // 3)
    offsets = sorted({0, quote_len // 4, quote_len // 2, (3 * quote_len) // 4, max(0, quote_len - shingle)})
    matcher = SequenceMatcher(None, autojunk=False)
    matcher.set_seq2(norm_quote)
    quote_numbers = _NUMBER_RE.findall(norm_quote)
    tried: set[int] = set()
    for off in offsets:
        anchor = norm_quote[off : off + shingle]
        if len(anchor) < 8:
            continue
        pos = norm_source.find(anchor)
        hits = 0
        while pos != -1 and hits < 20:
            hits += 1
            for start in (pos - off - 4, pos - off, pos - off + 4):
                start = max(0, start)
                if start in tried:
                    continue
                tried.add(start)
                # Two window sizes: the quote's own length, and a longer one that
                # tolerates short omissions (e.g. a dropped "(Author et al., 2020)").
                for width in (quote_len, int(quote_len * 1.15) + 8):
                    window = norm_source[start : start + width]
                    matcher.set_seq1(window)
                    if matcher.real_quick_ratio() < fuzzy_threshold or matcher.quick_ratio() < fuzzy_threshold:
                        continue
                    if matcher.ratio() < fuzzy_threshold:
                        continue
                    # Near-verbatim is fine for wording, never for numbers: every
                    # number in the quote must occur in the matched passage.
                    wider = norm_source[max(0, start - 20) : start + width + 20]
                    window_numbers = set(_NUMBER_RE.findall(wider))
                    if all(n in window_numbers for n in quote_numbers):
                        return True
            pos = norm_source.find(anchor, pos + 1)

    return False


def _check_evidence(evidence: Evidence, source_text: str, normalized_source: Optional[str] = None) -> bool:
    """Verify a single Evidence object against source text."""
    if not evidence or not evidence.quote:
        return False
    return verify_evidence(evidence.quote, source_text, normalized_source=normalized_source)


def _number_variants(value: float) -> set[str]:
    """String forms a reported number may take in the text (8.30 -> 8.3, 1200 -> 1,200)."""
    variants = {repr(value), f"{value:g}"}
    if float(value).is_integer():
        iv = int(value)
        variants.update({str(iv), f"{iv:,}", f"{iv}.0"})
    else:
        for digits in range(1, 5):
            variants.add(f"{value:.{digits}f}")
    return {v for v in variants if v}


# "7.4 × 10−4", "7.4×10^-4", "7.4 x 10(-4)", "10⁻⁴" (mantissa optional) and "7.4e-4".
_SCI_RE = re.compile(
    r"(?:(?P<mant>\d+(?:\.\d+)?)\s*[×x·*]\s*)?10\s*\^?\s*\(?\s*(?P<exp>[-+]?\s?\d{1,3})\s*\)?"
    r"|(?P<emant>\d+(?:\.\d+)?)[eE](?P<eexp>[-+]?\d{1,3})"
)


def _scientific_values(quote: str) -> list[float]:
    """Numbers the quote writes in scientific notation, as floats."""
    text = quote.translate(str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺", "0123456789-+"))
    for ch in "\u2212\u2013\u2011\u2010\u2012":
        text = text.replace(ch, "-")
    values = []
    for m in _SCI_RE.finditer(text):
        if m.group("eexp") is not None:
            values.append(float(m.group("emant")) * 10.0 ** int(m.group("eexp")))
        else:
            exp = int(m.group("exp").replace(" ", ""))
            if m.group("mant") is None and exp >= 0:
                continue  # a bare "10" or "10 3" is not scientific notation
            values.append(float(m.group("mant") or 1) * 10.0 ** exp)
    return values


def value_in_quote(value: float, quote: str) -> bool:
    """True if the metric's numeric value appears in its evidence quote, written
    plainly (8.3, 8.30, 1,200) or in scientific notation (7.4 × 10−4 for 0.00074)."""
    if not quote:
        return False
    numbers_in_quote = set(re.findall(r"\d[\d,]*(?:\.\d+)?", quote))
    stripped = {n.rstrip("0").rstrip(".") if "." in n else n for n in numbers_in_quote}
    for v in _number_variants(abs(value)):
        if v in numbers_in_quote or v in stripped:
            return True
        vv = v.rstrip("0").rstrip(".") if "." in v else v
        if vv in stripped:
            return True
    target = abs(value)
    return any(abs(sv - target) <= 1e-9 * max(abs(sv), target) for sv in _scientific_values(quote))


def _name_in_quote(name: str, quote: str) -> bool:
    return bool(name) and _normalize_text(name) in _normalize_text(quote)


def _sentence_with_name(name: str, source_text: str, max_len: int = 400) -> Optional[str]:
    """A real sentence from the source that mentions `name` (whole word, case-sensitive)."""
    m = re.search(rf"(?<![\w-]){re.escape(name)}(?![\w-])", source_text)
    if not m:
        return None
    text = re.sub(r"(\w)-\s*\n\s*(\w)", r"\1\2", source_text)
    m = re.search(rf"(?<![\w-]){re.escape(name)}(?![\w-])", text)
    if not m:
        return None
    left = max(text.rfind(". ", 0, m.start()), text.rfind("\n\n", 0, m.start()))
    left = 0 if left == -1 else left + 1
    ends = [i for i in (text.find(". ", m.end()), text.find("\n\n", m.end())) if i != -1]
    right = min(ends) + 1 if ends else len(text)
    sentence = re.sub(r"\s+", " ", text[left:right]).strip()
    if len(sentence) > max_len:
        center = m.start() - left
        lo = max(0, center - max_len // 2)
        sentence = sentence[lo : lo + max_len].strip()
    return sentence or None


def _locate_section(quote: str, sections: Optional[PaperSections]) -> tuple[str, Optional[int]]:
    """Find which parsed section/page actually contains the quote."""
    if not sections or not sections.sections:
        return "", None
    norm_quote = _normalize_text(quote)[:120]
    if not norm_quote:
        return "", None
    for sec in sections.sections:
        if norm_quote in _normalize_text(sec.text):
            heading = sec.heading
            page = sec.page
            if heading.lower().startswith("page ") and page is not None:
                heading = ""
            return heading, page
    return "", None


def verify_and_filter(
    extraction: PaperExtraction,
    source_text: str,
    sections: Optional[PaperSections] = None,
) -> tuple[PaperExtraction, VerificationReport]:
    """Verify all evidence quotes in an extraction and drop unverifiable items.

    Checks every Evidence quote against the full paper text.
    Items with unverifiable quotes are dropped from the extraction.

    Args:
        extraction: The paper extraction to verify.
        source_text: The full text of the paper (or abstract if no full text).

    Returns:
        Tuple of (filtered_extraction, verification_report).
    """
    paper_id = extraction.paper_id
    total_items = 0
    verified_items = 0
    normalized_source = _normalize_text(source_text)

    def ok(evidence: Evidence) -> bool:
        if not _check_evidence(evidence, source_text, normalized_source):
            return False
        # Record where the quote really is, rather than trusting a model-provided label.
        heading, page = _locate_section(evidence.quote, sections)
        if heading or page is not None:
            evidence.section = heading
            evidence.page = page
        elif sections is not None:
            evidence.section = ""
        return True

    # Verify problem
    verified_problem = extraction.problem
    if extraction.problem:
        total_items += 1
        if ok(extraction.problem.evidence):
            verified_items += 1
        else:
            logger.debug("[%s] Dropped problem: quote not found in text", paper_id)
            verified_problem = None

    # Verify method
    verified_method = extraction.method
    if extraction.method:
        total_items += 1
        if ok(extraction.method.evidence):
            verified_items += 1
        else:
            logger.debug("[%s] Dropped method: quote not found in text", paper_id)
            verified_method = None

    # Verify technology
    verified_tech = extraction.technology
    if extraction.technology:
        total_items += 1
        if ok(extraction.technology.evidence):
            verified_items += 1
        else:
            logger.debug("[%s] Dropped technology: quote not found in text", paper_id)
            verified_tech = None

    # Verify metrics
    verified_metrics: list[Metric] = []
    for metric in extraction.metrics:
        total_items += 1
        if value_in_quote(metric.value, metric.evidence.quote) and ok(metric.evidence):
            verified_items += 1
            verified_metrics.append(metric)
        else:
            logger.debug("[%s] Dropped metric '%s': quote not found", paper_id, metric.name)

    # Verify findings
    verified_findings: list[Finding] = []
    for finding in extraction.findings:
        total_items += 1
        if ok(finding.evidence):
            verified_items += 1
            verified_findings.append(finding)
        else:
            logger.debug("[%s] Dropped finding '%s': quote not found", paper_id, finding.id)

    # Verify limitations
    verified_limitations: list[Limitation] = []
    for limitation in extraction.limitations:
        total_items += 1
        if ok(limitation.evidence):
            verified_items += 1
            verified_limitations.append(limitation)
        else:
            logger.debug("[%s] Dropped limitation: quote not found", paper_id)

    # Verify tools
    verified_tools: list[ToolRef] = []
    for tool in extraction.tools:
        total_items += 1
        if _name_in_quote(tool.name, tool.evidence.quote) and ok(tool.evidence):
            verified_items += 1
            verified_tools.append(tool)
        elif (sentence := _sentence_with_name(tool.name, source_text)) is not None:
            # The name is really in the paper even though the model's quote was
            # not verbatim: keep the item, with the paper's own sentence as evidence.
            tool.evidence.quote = sentence
            ok(tool.evidence)
            verified_items += 1
            verified_tools.append(tool)
        else:
            logger.debug("[%s] Dropped tool '%s': quote not found", paper_id, tool.name)

    # Verify datasets
    verified_datasets: list[DatasetRef] = []
    for dataset in extraction.datasets:
        total_items += 1
        if _name_in_quote(dataset.name, dataset.evidence.quote) and ok(dataset.evidence):
            verified_items += 1
            verified_datasets.append(dataset)
        elif (sentence := _sentence_with_name(dataset.name, source_text)) is not None:
            # The name is really in the paper even though the model's quote was
            # not verbatim: keep the item, with the paper's own sentence as evidence.
            dataset.evidence.quote = sentence
            ok(dataset.evidence)
            verified_items += 1
            verified_datasets.append(dataset)
        else:
            logger.debug("[%s] Dropped dataset '%s': quote not found", paper_id, dataset.name)

    dropped = total_items - verified_items
    drop_rate = dropped / total_items if total_items > 0 else 0.0

    filtered = PaperExtraction(
        paper_id=paper_id,
        problem=verified_problem,
        method=verified_method,
        technology=verified_tech,
        tools=verified_tools,
        datasets=verified_datasets,
        metrics=verified_metrics,
        findings=verified_findings,
        limitations=verified_limitations,
        extracted_by=extraction.extracted_by,
    )

    report = VerificationReport(
        paper_id=paper_id,
        total_items=total_items,
        verified_items=verified_items,
        dropped_items=dropped,
        drop_rate=drop_rate,
    )

    if dropped > 0:
        logger.info(
            "[%s] Evidence verification: %d/%d items kept (%.0f%% drop rate)",
            paper_id,
            verified_items,
            total_items,
            drop_rate * 100,
        )

    return filtered, report
