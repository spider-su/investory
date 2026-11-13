"""Conservative, deterministic output validation for v1.

This is not a full semantic entailment checker. It fails closed when citations,
amounts, missing facts or explicit source conflicts make an answer unsafe.
"""
from collections import defaultdict
from decimal import Decimal, InvalidOperation
import re

from .reliability import requested_fact_fields

CITATION = re.compile(r"\[([^\[\]]+)\]")
NUMBER = re.compile(r"[-+]?(?:\d[\d,]*(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?")
GROUPED_NUMBER = re.compile(r"[-+]?\d{1,3}(?:,\d{3})+(?:\.\d*)?(?:[eE][-+]?\d+)?")
CURRENCY = re.compile(r"(?<![A-Za-z])(?:USD|EUR|PLN|GBP|CHF|JPY|CAD|AUD|CNY)(?![A-Za-z])|[$€£]", re.I)
ASSERTION = re.compile(r"^\s*([A-Za-z][\w -]{3,60}):\s*(\d[\d.,]*(?:\s*[A-Za-z%]+)?)\s*$", re.M)
INJECTION = re.compile(
    r"(?:ignore (?:all |any )?(?:previous|prior|system) instructions|"
    r"override (?:the )?(?:system|developer) (?:instructions|message)|"
    r"you are now (?:the|a) system|"
    r"reveal (?:your|the) (?:secret|api key|system prompt)|"
    r"send (?:the|your) (?:token|secret|password) to)",
    re.I,
)
NO_VALUE = {"unavailable", "none", "null", ""}


def filter_untrusted_chunks(chunks: list[dict]) -> list[dict]:
    return [chunk for chunk in chunks if not INJECTION.search(chunk["text"])]


def conflicting_claims(chunks: list[dict]) -> bool:
    """Detect explicitly keyed, same-topic contradictory source assertions."""
    claims = defaultdict(lambda: defaultdict(set))
    for chunk in chunks:
        for key, value in ASSERTION.findall(chunk["text"]):
            claims[key.strip().lower()][value.strip().lower()].add(chunk["source"])
    for values in claims.values():
        if len(values) > 1 and len(set().union(*values.values())) > 1:
            return True
    return False


def missing_requested_fact(question: str, facts: dict[str, str]) -> bool:
    return any(str(facts.get(field, "unavailable")).lower() in NO_VALUE
               for field in requested_fact_fields(question))


def facts_for_question(question: str, facts: dict[str, str]) -> dict[str, str]:
    # A known value for another aggregate must never justify the requested amount.
    return {key: value for key, value in facts.items()
            if key == "currency" or key in requested_fact_fields(question)}


def _amounts(text: str, strict: bool = True) -> set[Decimal] | None:
    values = set()
    for match in NUMBER.finditer(text):
        token = match.group().rstrip(",")
        if "," in token and not GROUPED_NUMBER.fullmatch(token):
            if strict:
                return None
            continue
        if token.startswith(("-", "+")) and match.start() and text[match.start() - 1].isdigit():
            token = token[1:]  # a year/range separator, not a negative amount
        try:
            value = Decimal(token.replace(",", ""))
            if not value.is_finite():
                return None
            values.add(value)
        except InvalidOperation:
            return None
    return values


def validate_answer(
    answer: str,
    allowed_sources: list[str],
    facts: dict[str, str] | None = None,
    supporting_texts: list[str] | None = None,
) -> bool:
    """Reject missing/unknown citations and invented numeric outputs.

    For live-financial answers only allow numerals already supplied by Java.
    We cannot prove all prose is entailed: this is a guard plus test harness.
    """
    cited = set(CITATION.findall(answer))
    if (not answer.strip() or INJECTION.search(answer) or not cited
            or not cited.issubset(set(allowed_sources))):
        return False
    if facts is None:
        # A cited source is not sufficient to justify invented numeric claims.
        # This catches literal amount/year hallucinations, not semantic distortions.
        if supporting_texts is not None:
            supported = _amounts(" ".join(supporting_texts), strict=False)
            actual = _amounts(CITATION.sub("", answer))
            return actual is not None and actual.issubset(supported)
        return True
    if "live:profile" not in cited:
        return False
    if re.search(r"%|\bpercent(?:age)?\b", answer, re.I):
        return False
    currency = facts.get("currency", "")
    values = set()
    for key, value in facts.items():
        if key == "currency" or str(value).lower() in NO_VALUE:
            continue
        parsed = _amounts(str(value))
        if parsed is None:
            return False
        values.update(parsed)
    actual = _amounts(CITATION.sub("", answer))
    if actual is None or not actual.issubset(values):
        return False
    symbols = {"$": "USD", "€": "EUR", "£": "GBP"}
    currencies = {symbols.get(code, code.upper()) for code in CURRENCY.findall(answer)}
    if currencies and currencies != {currency}:
        return False
    if actual and currencies != {currency}:
        return False
    return True
