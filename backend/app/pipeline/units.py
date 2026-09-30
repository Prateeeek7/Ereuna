"""Unit normalizer for common scientific measurements.

Normalizes SI-prefixed units (W, A, V, s, m, F, Hz) to their base form,
handles percentages and decibels, and parses unit strings from extracted text.
"""

import re
from typing import Optional

# SI prefix multipliers
SI_PREFIXES: dict[str, float] = {
    "f": 1e-15,   # femto
    "p": 1e-12,   # pico
    "n": 1e-9,    # nano
    "µ": 1e-6,    # micro
    "u": 1e-6,    # micro (ASCII alternative)
    "μ": 1e-6,    # micro (Greek mu)
    "m": 1e-3,    # milli
    "c": 1e-2,    # centi
    "k": 1e3,     # kilo
    "K": 1e3,     # kilo (uppercase variant)
    "M": 1e6,     # mega
    "G": 1e9,     # giga
    "T": 1e12,    # tera
}

# Base units that accept SI prefixes
BASE_UNITS: set[str] = {"W", "A", "V", "s", "m", "F", "Hz", "eV", "Ω", "ohm", "S"}

# Units that should NOT be normalized (no prefix conversion)
PASSTHROUGH_UNITS: set[str] = {"%", "dB", "dBm", "°C", "°K", "K", "ratio", "x", "X", "cycles"}

# Common unit aliases
UNIT_ALIASES: dict[str, str] = {
    "watt": "W",
    "watts": "W",
    "amp": "A",
    "amps": "A",
    "ampere": "A",
    "amperes": "A",
    "volt": "V",
    "volts": "V",
    "second": "s",
    "seconds": "s",
    "sec": "s",
    "meter": "m",
    "meters": "m",
    "farad": "F",
    "farads": "F",
    "hertz": "Hz",
    "ohm": "Ω",
    "ohms": "Ω",
    "siemens": "S",
    "percent": "%",
    "pct": "%",
}

# Compound units: recognize common forms
COMPOUND_UNITS: dict[str, str] = {
    "W/cell": "W/cell",
    "W/bit": "W/bit",
    "W/MHz": "W/MHz",
    "A/cell": "A/cell",
    "V/s": "V/s",
    "m²": "m²",
    "m^2": "m²",
    "cm²": "cm²",
    "cm^2": "cm²",
    "µm²": "µm²",
    "um²": "µm²",
    "nm²": "nm²",
}

# Pattern: optional number, optional SI prefix, base unit, optional compound suffix
_UNIT_RE = re.compile(
    r"^([fpnuµμmckKMGT])?"        # optional SI prefix
    r"(W|A|V|s|m|F|Hz|eV|Ω|ohm|S)"  # base unit
    r"(/[a-zA-Z]+(?:\^?\d)?)?$"   # optional compound denominator
)

# Pattern: extract value + unit from a string like "12.3 pW/cell"
_VALUE_UNIT_RE = re.compile(
    r"(-?\d+\.?\d*(?:[eE][+-]?\d+)?)"   # numeric value
    r"\s*"
    r"([a-zA-Zµμ°Ω%/²^]+\d*)"          # unit string
)


# ──────────────────────────────────────────────────────────
# Unit spelling
# ──────────────────────────────────────────────────────────

_MINUS = "\u2212\u2013\u2011\u2010\u2012"
_SUPERSCRIPTS = str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺", "0123456789-+")
_EXP_GLYPH = {"2": "²", "3": "³"}
# One factor of a product unit: letters (with µ, Ω, °, %) and an optional integer exponent.
_FACTOR_RE = re.compile(r"^(?P<base>[A-Za-zµμΩ°%]+)(?P<exp>-?\d)?$")
_SCI_IN_UNIT_RE = re.compile(r"^\s*[×x*·]\s*10\s*\^?\s*\(?\s*(?P<exp>[-+]?\d{1,3})\s*\)?\s*(?P<rest>.*)$")


def _plain(text: str) -> str:
    """ASCII minus signs, superscripts as digits, no carets."""
    for ch in _MINUS:
        text = text.replace(ch, "-")
    return text.translate(_SUPERSCRIPTS).replace("^", "")


def fold_scientific(value: float, unit: str) -> tuple[float, str]:
    """Move a power of ten written into the unit into the value.

    Extractors sometimes return value 7.4 with unit "× 10−4 S cm−1"; that is 7.4e-4 S/cm.
    """
    m = _SCI_IN_UNIT_RE.match(_plain(unit or ""))
    if not m:
        return value, unit
    return value * (10.0 ** int(m.group("exp"))), m.group("rest").strip()


def canonical_unit(unit: str) -> str:
    """One spelling per unit: "S cm−1" and "S cm^-1" -> "S/cm", "mA cm-2" -> "mA/cm²",
    "ohm·cm2" -> "Ω·cm²". Units it cannot parse are returned trimmed, unchanged."""
    raw = (unit or "").strip()
    if not raw:
        return ""
    text = _plain(raw).replace("⋅", "·").replace("*", "·")
    text = re.sub(r"\s*·\s*", "·", text)
    if "/" in text:
        num, _, den = text.partition("/")
        num_tokens = [t for t in re.split(r"[\s·]+", num.strip()) if t]
        den_tokens = [t for t in re.split(r"[\s·]+", den.strip()) if t]
        den_tokens = [t if re.search(r"-\d$", t) else re.sub(r"(\d)$", r"-\1", t) if re.search(r"[A-Za-z]\d$", t) else t + "-1"
                      for t in den_tokens]
        tokens = num_tokens + den_tokens
    else:
        tokens = [t for t in re.split(r"[\s·]+", text) if t]
    numerator: list[str] = []
    denominator: list[str] = []
    for tok in tokens:
        m = _FACTOR_RE.match(tok)
        if not m:
            return raw
        base = m.group("base")
        base = {"ohm": "Ω", "ohms": "Ω", "Ohm": "Ω"}.get(base, base)
        exp = int(m.group("exp") or 1)
        target = denominator if exp < 0 else numerator
        power = abs(exp)
        target.append(base + ("" if power == 1 else _EXP_GLYPH.get(str(power), str(power))))
    if not numerator and not denominator:
        return raw
    result = "·".join(numerator) if numerator else "1"
    if denominator:
        result += "/" + "·".join(denominator)
    return result


# Units whose SI prefix can be folded into the value (mAh -> Ah, mS -> S, mg -> g).
_PREFIXABLE = BASE_UNITS | {"Ah", "Wh", "J", "Pa", "g", "mol", "L", "eV"}


def normalize_metric(value: float, unit: str) -> tuple[float, str]:
    """Scientific notation folded in, unit spelled canonically, SI prefix of the
    leading unit folded into the value (12.3 pW -> 1.23e-11 W, 7.4 mS cm−1 -> 7.4e-3 S/cm)."""
    value, unit = fold_scientific(value, unit)
    unit = canonical_unit(unit)
    if not unit or unit in PASSTHROUGH_UNITS:
        return _tidy(value), unit
    head, sep, tail = unit.partition("/")
    first, dot, rest = head.partition("·")
    if first not in _PREFIXABLE and len(first) >= 2 and first[0] in SI_PREFIXES and first[1:] in _PREFIXABLE:
        return _tidy(value * SI_PREFIXES[first[0]]), first[1:] + dot + rest + sep + tail
    v, u = _normalize_simple(value, unit)
    return _tidy(v), u


def _tidy(value: float) -> float:
    """Drop float noise from prefix arithmetic (7.4 * 1e-4 -> 0.00074)."""
    return float(f"{value:.12g}")


def _normalize_simple(value: float, unit: str) -> tuple[float, str]:
    """Normalize a metric value and unit to base SI form.

    Converts prefixed units to their base form (e.g., 12.3 pW -> 12.3e-12 W).
    Passthrough units (%, dB) are returned unchanged.

    Args:
        value: The numeric value.
        unit: The unit string (e.g., "pW", "µA", "nm", "dB", "%").

    Returns:
        Tuple of (normalized_value, normalized_unit).
    """
    unit = unit.strip()

    if not unit:
        return value, unit

    # Check passthrough units first
    if unit in PASSTHROUGH_UNITS:
        return value, unit

    # Check unit aliases
    lower_unit = unit.lower()
    if lower_unit in UNIT_ALIASES:
        unit = UNIT_ALIASES[lower_unit]
        if unit in PASSTHROUGH_UNITS:
            return value, unit

    # Try to parse as prefixed unit
    match = _UNIT_RE.match(unit)
    if match:
        prefix = match.group(1)
        base = match.group(2)
        suffix = match.group(3) or ""

        if prefix and prefix in SI_PREFIXES:
            multiplier = SI_PREFIXES[prefix]
            return value * multiplier, f"{base}{suffix}"
        else:
            # No prefix, already base unit
            return value, unit

    # Handle compound units with prefix in the numerator
    # e.g., "pW/cell" -> parse the prefix from the first part
    if "/" in unit:
        parts = unit.split("/", 1)
        num_part = parts[0]
        den_part = parts[1]

        if len(num_part) >= 2:
            maybe_prefix = num_part[0]
            maybe_base = num_part[1:]
            if maybe_prefix in SI_PREFIXES and maybe_base in BASE_UNITS:
                multiplier = SI_PREFIXES[maybe_prefix]
                return value * multiplier, f"{maybe_base}/{den_part}"

    # Could not normalize — return as-is
    return value, unit


def parse_value_unit(text: str) -> Optional[tuple[float, str]]:
    """Parse a numeric value and unit from a text string.

    Args:
        text: String like "12.3 pW/cell" or "0.6 V" or "45 nm".

    Returns:
        Tuple of (value, unit) or None if parsing fails.
    """
    text = text.strip()
    match = _VALUE_UNIT_RE.search(text)
    if match:
        try:
            value = float(match.group(1))
            unit = match.group(2)
            return value, unit
        except ValueError:
            return None
    return None


def normalize_text_metric(text: str) -> Optional[tuple[float, str, float, str]]:
    """Parse and normalize a metric from a text string.

    Args:
        text: String like "12.3 pW/cell".

    Returns:
        Tuple of (raw_value, raw_unit, normalized_value, normalized_unit)
        or None if parsing fails.
    """
    parsed = parse_value_unit(text)
    if parsed is None:
        return None

    raw_value, raw_unit = parsed
    norm_value, norm_unit = normalize_metric(raw_value, raw_unit)
    return raw_value, raw_unit, norm_value, norm_unit
