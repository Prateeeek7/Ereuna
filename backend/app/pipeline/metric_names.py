"""Metric names that line up across papers.

Papers (and models extracting from them) tend to fold the sample and the test
condition into the metric name: "capacity retention of the PEO cell after 20
cycles". Two papers then never share a name, so nothing can be compared. This
splits such a name into the measured quantity ("capacity retention") and a
qualifier ("the PEO cell after 20 cycles") that belongs in the metric's subject
or conditions, and gives a comparison key for grouping equal quantities.
"""

import re

# Where a qualifier starts. "per" is deliberately absent: "mass per ton" is the quantity.
_CONNECTORS = (
    " of ", " for ", " in ", " at ", " after ", " under ", " with ", " using ", " on ",
    " vs ", " vs. ", " versus ", " compared ", " between ", " from ", " when ", " during ",
)

# Quantities whose own name contains a connector.
_PROTECTED = (
    "state of charge", "depth of discharge", "figure of merit", "coefficient of", "number of",
    "degree of", "rate of", "fraction of", "percentage of", "loss of", "density of states",
    "time of flight", "speed of", "limit of detection", "quality of", "point of", "angle of",
    "index of", "modulus of", "energy of", "heat of", "enthalpy of", "entropy of", "degree of polymerization",
    "area under", "time to", "onset of",
)

# A short all-caps parenthetical is an abbreviation ("(CE)", "(PCE)"): dropped.
_ABBREVIATION_RE = re.compile(r"\s*\((?:[A-Z][A-Za-z0-9+\-]{0,7}s?)\)")
_PAREN_RE = re.compile(r"\s*\(([^()]*)\)")

# Different spellings of the same quantity, compared by key only (display keeps the paper's wording).
_SYNONYMS = {
    "li-ion conductivity": "ionic conductivity",
    "li+ conductivity": "ionic conductivity",
    "lithium-ion conductivity": "ionic conductivity",
    "ion conductivity": "ionic conductivity",
    "ce": "coulombic efficiency",
    "pce": "power conversion efficiency",
    "ccd": "critical current density",
    "top1 accuracy": "top-1 accuracy",
    "top 1 accuracy": "top-1 accuracy",
}


def split_metric_name(name: str) -> tuple[str, str]:
    """(quantity, qualifier) with the paper's casing kept, e.g.
    "Critical current density of LiF-coated LPS" -> ("Critical current density", "LiF-coated LPS")."""
    text = " ".join((name or "").split())
    qualifiers: list[str] = []

    text = _ABBREVIATION_RE.sub("", text)
    for inner in _PAREN_RE.findall(text):
        if inner.strip():
            qualifiers.append(inner.strip())
    text = _PAREN_RE.sub("", text).strip()

    lower = f" {text.lower()} "
    protected_until = 0
    for phrase in _PROTECTED:
        idx = lower.find(f" {phrase}")
        if idx != -1:
            protected_until = max(protected_until, idx + len(phrase) + 1)

    cut = None
    for connector in _CONNECTORS:
        start = 0
        while True:
            idx = lower.find(connector, start)
            if idx == -1:
                break
            # "mass per ton of lithium": the "of" belongs to the rate, not a qualifier.
            after_per = re.search(r"\bper \S+$", lower[:idx]) is not None
            if idx >= protected_until and idx > 0 and not after_per:
                cut = idx if cut is None else min(cut, idx)
                break
            start = idx + 1
    if cut is not None:
        head = text[: cut - 1].strip() if cut >= 1 else text
        tail = text[cut - 1 :].strip()
        # Drop the connector word itself from the qualifier ("of", "after" -> keep "after": it reads as a condition).
        first, _, rest = tail.partition(" ")
        tail = rest if first.lower() in ("of", "for", "in", "with", "using", "on", "from", "between") else tail
        if head:
            qualifiers.insert(0, tail)
            text = head

    return text.strip(" ,;:-"), ", ".join(q for q in qualifiers if q)


def metric_key(name: str) -> str:
    """Comparison key for a quantity name: lowercase, plain spacing, known synonyms merged."""
    quantity, _ = split_metric_name(name)
    key = re.sub(r"[^a-z0-9%+\- ]", " ", quantity.lower())
    key = re.sub(r"\s*-\s*", "-", " ".join(key.split()))
    key = re.sub(r"^(?:the|a|an|measured|reported|average|mean|overall|total) ", "", key)
    return _SYNONYMS.get(key, key)


_CONDITION_START = ("at ", "after ", "under ", "during ", "when ", "before ", "upon ", "over ", "within ", "vs ", "versus ")


def structure_metric(name: str, subject: str = "", other: str | None = None) -> tuple[str, str, str | None]:
    """(quantity, subject, other conditions) for an extracted metric.

    A qualifier found in the name moves to the subject ("of LiF-coated LPS") or,
    when it reads as a test condition ("after 20 cycles"), to the conditions.
    """
    quantity, qualifier = split_metric_name(name)
    subject = (subject or "").strip()
    other = (other or "").strip()
    if qualifier:
        if qualifier.lower().startswith(_CONDITION_START):
            if qualifier.lower() not in other.lower():
                other = f"{other}; {qualifier}" if other else qualifier
        elif not subject:
            subject = qualifier
        elif qualifier.lower() not in subject.lower() and qualifier.lower() not in other.lower():
            other = f"{other}; {qualifier}" if other else qualifier
    return quantity or " ".join((name or "").split()), subject, other or None
