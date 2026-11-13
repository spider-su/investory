"""Conservative, deterministic output validation for v1.

This is not a full semantic entailment checker. It fails closed when citations,
amounts, missing facts or explicit source conflicts make an answer unsafe.
"""
from collections import defaultdict
from decimal import Decimal, InvalidOperation
import re

CITATION = re.compile(r"\[([^\[\]]+)\]")
NUMBER = re.compile(r"(?<![\w])[-+]?\d[\d,]*(?:\.\d+)?(?![\w])")
ASSERTION = re.compile(r"^\s*([A-Za-z][\w -]{3,60}):\s*(\d[\d.,]*(?:\s*[A-Za-z%]+)?)\s*$", re.M)
INJECTION = re.compile(
    r"(?:ignore (?:all |any )?(?:previous|prior|system) instructions|"
    r"override (?:the )?(?:system|developer) (?:instructions|message)|"
    r"you are now (?:the|a) system|"
    r"reveal (?:your|the) (?:secret|api key|system prompt)|"
    r"send (?:the|your) (?:token|secret|password) to)",
    re.I,
)
FIELD_TERMS = {
    "marketPortfolioValue": ("market portfolio", "portfolio value"),
    "longTermAssetValue": ("long-term asset", "long term asset"),
    "totalNetWorth": ("net worth", "total wealth", "am i worth"),
    "liquidAssets": ("liquid asset",),
    "illiquidAssets": ("illiquid asset",),
}
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
    normalized = question.lower()
    for field, names in FIELD_TERMS.items():
        if any(name in normalized for name in names):
            if str(facts.get(field, "unavailable")).lower() in NO_VALUE:
                return True
    return False


def _amounts(text: str) -> set[Decimal]:
    values = set()
    for token in NUMBER.findall(text):
        try:
            values.add(Decimal(token.replace(",", "")))
        except InvalidOperation:
            continue
    return values


def validate_answer(
    answer: str,
    allowed_sources: list[str],
    facts: dict[str, str] | None = None,
) -> bool:
    """Reject missing/unknown citations and invented numeric outputs.

    For live-financial answers only allow numerals already supplied by Java.
    We cannot prove all prose is entailed: this is a guard plus test harness.
    """
    cited = set(CITATION.findall(answer))
    if not answer.strip() or not cited or not cited.issubset(set(allowed_sources)):
        return False
    if facts is None:
        return True
    if "live:profile" not in cited:
        return False
    currency = facts.get("currency", "")
    values = set()
    for key, value in facts.items():
        if key == "currency" or str(value).lower() in NO_VALUE:
            continue
        values.update(_amounts(str(value)))
    if not _amounts(CITATION.sub("", answer)).issubset(values):
        return False
    if _amounts(CITATION.sub("", answer)) and currency not in answer:
        return False
    return True
