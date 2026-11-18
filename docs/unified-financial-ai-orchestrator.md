# Unified financial assistant

The portfolio panel uses `POST /api/v1/portfolios/{portfolioId}/assistant/ask`.
Documentation-only mode retains `/rag/ask-docs`. Existing RAG/research/MCP routes
remain supported.

```json
{"question":"Show my net worth and holdings and explain internal transfers"}
```

One authenticated question can select several existing read-only sources:

- Java Profile, Investment and Retirement APIs own amounts, currency, current
  holdings, period benchmark methodology, investment profit and saved assumptions.
  Exact decimal values are copied; no model arithmetic or new valuation is used.
- Explicit documentation clauses use Python RAG. Known personal-value explanations
  become public methodology questions; private snapshots are never sent to that
  documentation route. Unknown questions retain the filtered profile-facts path.
- Explicit quote requests use `ExternalMarketDataService`, including its durable
  cache, attempted-call budget and coverage policy. Only the public symbol leaves
  Java. The largest holding requires a verified mapping from its canonical listing to the provider symbol.

Responses include `answer`, sources, components, optional typed `marketData`,
and `marketStatus` for market requests. The panel shows decimal price,
currency when verified, trading date, source and cache status. Provider
observations appear separately from Investory values and citations. The
coverage status shows ticker or provider cooldowns, remaining attempts and
when a full local budget next frees a slot. Provider absence or quota
exhaustion leaves the Java facts available.

Request validation and strict active membership checks precede source reads.
The route shares existing per-user rate/concurrency admission and always releases
its permit. Basic/bearer read-only requests have the existing narrow CSRF exemption;
browser-cookie requests still require CSRF. Unsupported periods, forecasts and
trading decisions abstain before any source lookup.

Explicit profit/loss and SPY comparison questions can use a single dashboard period:
`1M`, `3M`, `6M`, `YTD` (including “this year”), `1Y`, `3Y`, `5Y`, or `MAX`.
The answer labels the selected period. Requests with conflicting periods,
arbitrary calendar dates or historical holdings abstain. Other composite
questions retain their existing source routing.

This is bounded, stateless orchestration, not an unrestricted agent, multi-turn
memory, trade execution or cross-source financial inference. It calls shared Java
application reads directly, preserving the same ownership boundary as own MCP;
it does not make loopback calls to its MCP transport. Supported source sets can
be combined, but arbitrary tool chains and calendar-year reporting are not added.

See the [market policy](../services/rag/README.md#durable-market-policy-java-gateway)
and [MCP contract](../adapters/mcp/README.md) for the canonical underlying limits.
Release gates require exact-head CI, scanned Java/RAG publication, runtime image-ID
matching, authenticated API/official-MCP acceptance and a fresh private metrics
scrape. A merged PR alone is not deployment evidence.
