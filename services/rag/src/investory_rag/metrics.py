"""Low-cardinality observability. Never label metrics with questions or financial data."""
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST

REQUESTS = Counter(
    "investory_rag_requests_total", "Internal RAG request outcomes",
    ["endpoint", "outcome"],
)
LATENCY = Histogram(
    "investory_rag_request_seconds", "Internal RAG request elapsed time",
    ["endpoint"],
    buckets=(0.1, 0.25, 0.5, 1, 2, 5, 10, 20, 30, 60),
)
TOKENS = Counter(
    "investory_rag_tokens_total", "Provider-reported aggregate token usage",
    ["direction"],
)


def record_usage(message) -> None:
    """Only aggregate usage totals; never record model content, subject or identity."""
    usage = getattr(message, "usage_metadata", None)
    if not isinstance(usage, dict):
        return
    for label, field in (("input", "input_tokens"), ("output", "output_tokens")):
        count = usage.get(field)
        if isinstance(count, int) and not isinstance(count, bool) and count > 0:
            TOKENS.labels(direction=label).inc(count)
