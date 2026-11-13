"""Conservative v1 question gates. Only document or Java-authorized totals are supported.

No provider calls, personal data, or vector-store access in the classifier.
This is a safety backstop, not a replacement for retrieval evaluation.
"""
import re

STOPWORDS = frozenset(
    "a an and are as at be by can do does for from how i in is it me my of on or "
    "please should show tell that the them there these this to was what when where "
    "which who why will with would you your about current investory".split()
)
LIVE_EXTERNAL = re.compile(
    r"\b(?:right now|today|yesterday|tomorrow|live price|latest price|"
    r"current (?:stock|share|market|brokerage|bank)|"
    r"brokerage account|bank account|market quote|real.time quote)\b",
    re.I,
)
ADVICE = re.compile(r"\b(?:should i (?:buy|sell)|what (?:stock|etf) (?:should|to) buy|best (?:stock|investment) for me)\b", re.I)
AMBIGUOUS = re.compile(
    r"^(?:what (?:is|about) (?:it|that|this)|which (?:one|option) is (?:best|better)|"
    r"tell me more|explain (?:it|that|this))\??$",
    re.I,
)
# These are the only authoritative facts available through Java's v1 adapter.
SUPPORTED_FACT_TOPICS = (
    "net worth", "market portfolio", "portfolio value", "long term asset",
    "long-term asset", "liquid asset", "illiquid asset", "asset value",
    "currency", "how much am i worth", "total wealth"
)


def terms(text: str) -> set[str]:
    return {word for word in re.findall(r"[a-z][a-z0-9-]{2,}", text.lower())
            if word not in STOPWORDS}


def unsupported_document_question(question: str) -> bool:
    question = question.strip()
    return (
        bool(LIVE_EXTERNAL.search(question))
        or bool(ADVICE.search(question))
        or bool(AMBIGUOUS.fullmatch(question))
        or len(terms(question)) < 2
    )


def unsupported_facts_question(question: str) -> bool:
    if unsupported_document_question(question):
        # A current net-worth question is allowed because Java supplied that value.
        if re.search(r"\bcurrent\s+(?:net worth|total wealth)\b", question, re.I):
            return False
        return True
    lowered = question.lower()
    return not any(term in lowered for term in SUPPORTED_FACT_TOPICS)


def has_textual_support(question: str, chunks: list[dict]) -> bool:
    """Require some lexical evidence in the returned excerpts, not just high vector score."""
    query_terms = terms(question)
    return bool(query_terms) and any(
        bool(query_terms & terms(chunk["text"])) for chunk in chunks
    )
