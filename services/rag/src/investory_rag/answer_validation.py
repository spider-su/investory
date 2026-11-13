"""Conservative, deterministic output validation for v1.

This is not a full semantic entailment checker. It fails closed when citations,
amounts, missing facts or explicit source conflicts make an answer unsafe.
"""
from collections import defaultdict
from decimal import Decimal, InvalidOperation
import re

from .reliability import FIELD_TERMS, requested_fact_fields

CITATION = re.compile(r"\[([^\[\]]+)\]")
NUMBER = re.compile(r"[-+]?(?:\d[\d,]*(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?")
GROUPED_NUMBER = re.compile(r"[-+]?\d{1,3}(?:,\d{3})+(?:\.\d*)?(?:[eE][-+]?\d+)?")
CURRENCY = re.compile(r"(?<![A-Za-z])(?:USD|EUR|PLN|GBP|CHF|JPY|CAD|AUD|CNY)(?![A-Za-z])|[$€£]", re.I)
ASSERTION = re.compile(r"^\s*([A-Za-z][\w -]{3,60}):\s*(\d[\d.,]*(?:\s*[A-Za-z%]+)?)\s*$", re.M)
INJECTION = re.compile(
    r"(?:ignore (?:all |any )?(?:previous|prior|system|developer) instructions|"
    r"(?:disregard|forget) (?:all |the |your )?(?:previous |prior )?(?:instructions|rules|policy)|"
    r"override (?:the )?(?:system|developer) (?:instructions|message)|"
    r"(?:you are now|new role is) (?:the|a)?\s*system|"
    r"(?:<|\[|\b)(?:system|developer)(?:>|:|\])|"
    r"\[INST\]|\[/INST\]|"
    r"reveal (?:your|the) (?:secret|api key|system prompt|prompt)|"
    r"send (?:the|your) (?:token|secret|password) to|"
    r"do not cite (?:any )?sources)",
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


def normalize_citations(answer: str, allowed_sources: list[str], evidence: list[dict]) -> str:
    """Canonicalize verified references without dropping unknown citations.

    Grouped source labels may use comma/semicolon separators. A section tag must
    occur in the excerpt from that exact source. Anything else remains unchanged
    for fail-closed validation.
    """
    contexts = defaultdict(list)
    for chunk in evidence:
        contexts[chunk["source"]].append(chunk["text"])
    allowed = set(allowed_sources)

    def canonical(part: str) -> str | None:
        if part in allowed:
            return part
        for source in allowed:
            prefix = source + " "
            if part.startswith(prefix):
                tag = part[len(prefix):]
                if (re.fullmatch(r"[A-Z]{2,10}-\d{1,4}", tag)
                        and any(re.search(r"\b" + re.escape(tag) + r"\b", text)
                                for text in contexts[source])):
                    return source
        return None

    def replace(match):
        label = match.group(1).strip()
        if label in allowed:
            return "[" + label + "]"
        parts = [canonical(part.strip()) for part in re.split(r"[;,]", label)]
        if all(part is not None for part in parts):
            return "".join("[" + part + "]" for part in dict.fromkeys(parts))
        return match.group(0)

    return CITATION.sub(replace, answer)



def _field_amounts_match(answer: str, facts: dict[str, str]) -> bool:
    """Bind each numeric field claim to that field, not just a pool of valid values.

    A combined answer must label its amounts. For multiple claims, disallow
    unlabeled numeric prefixes; ambiguous labelling fails closed. This does
    not attempt full natural-language entailment.
    """
    text = CITATION.sub("", answer)
    labels = []
    for field, names in FIELD_TERMS.items():
        for phrase in (*names, field):
            pattern = r"\b" + re.escape(phrase) + r"s?\b"
            for match in re.finditer(pattern, text, re.I):
                labels.append((match.start(), match.end(), field))
    # Collapse aliases that overlap, keeping the most explicit/longest match.
    labels.sort(key=lambda item: (item[0], -(item[1] - item[0])))
    nonoverlap = []
    for item in labels:
        if not nonoverlap or item[0] >= nonoverlap[-1][1]:
            nonoverlap.append(item)

    requested = set(facts) - {"currency"}
    if not nonoverlap:
        # One requested field is unambiguous even when the label is omitted.
        return len(requested) <= 1
    if len(requested) > 1 and _amounts(text[:nonoverlap[0][0]]):
        return False
    for index, (start, end, field) in enumerate(nonoverlap):
        following = text[end:nonoverlap[index + 1][0] if index + 1 < len(nonoverlap) else len(text)]
        amounts = _amounts(following)
        if amounts is None:
            return False
        if not amounts:
            continue
        supplied = facts.get(field)
        if supplied is None or str(supplied).lower() in NO_VALUE:
            return False
        expected = _amounts(str(supplied))
        if expected is None or not amounts.issubset(expected):
            return False
    return True


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
    # Preserve documented array/year notation verbatim instead of interpreting
    # it as provenance. It never substitutes for an actual source citation.
    if supporting_texts is not None:
        cited -= {label for label in cited
                  if re.fullmatch(r"n(?:[+-]\d+)?", label)
                  and any("[" + label + "]" in text for text in supporting_texts)}
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
    if not _field_amounts_match(answer, facts):
        return False
    return True
