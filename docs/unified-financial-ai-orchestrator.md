# Unified Financial AI Orchestrator (initial implementation)

New read-only endpoint: `POST /api/v1/portfolios/{portfolioId}/assistant/ask`

Example request:

```json
{"question":"Compare my portfolio with SPY and my retirement plan"}
```

Response: `{"answer":"...","sources":["live:investment-benchmark","live:retirement-plan"],"components":["JAVA_BENCHMARK","JAVA_RETIREMENT"]}`.

The server selects multiple pre-existing, read-only sources for one authenticated question:
- Java domain reporting is authoritative for portfolio holdings, benchmark performance, lifetime profitability, net worth and retirement plans.
- Python RAG handles documentation questions and unmatched questions through the existing authenticated, filtered profile-facts adapter.
- Financial MCP is accessed only through the existing Java ExternalMarketDataService (cache, quotas and provider policy). Market requests require a public symbol, SPY, or the current profile's largest-holding public symbol. No positions, values, credentials or account data are sent to the provider.

Guards: validate requests; check scoped authorization before *any* data access; reuse user rate and concurrency admission; release concurrent admission in `finally`; no write tools or model-generated tool calls; abstain from unsupported periods/forecasts; keep separate provenance per source. Optional provider failures degrade only the market portion of an otherwise valid answer.

This is a deliberately bounded orchestration MVP, **not** an unrestricted LLM agent or a fully conversational, multi-turn memory system. It concatenates separately attributable facts rather than allowing an LLM to recalculate money or invent cross-source financial conclusions. The existing `/rag/ask`, `/rag/ask-docs` and `/rag/research/*` endpoints remain unchanged.

Test focus: cross-profile denial, quota admission, combined Java benchmark/retirement, optional MCP failure without losing Java facts, safe unsupported requests and docs-only privacy.

Deployment is intentionally outside this PR; run existing CI, verify API behavior in a non-production environment and keep final production live regression independent.
