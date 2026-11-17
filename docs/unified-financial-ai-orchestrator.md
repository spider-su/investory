# Unified financial assistant

The portfolio panel uses `POST /api/v1/portfolios/{portfolioId}/assistant/ask`.
Documentation-only mode retains `/rag/ask-docs`. Existing RAG/research/MCP routes
remain supported.

```json
{"question":"Show my net worth and holdings and explain internal transfers"}
```

One authenticated question can select several existing read-only sources:

- Java Profile, Investment and Retirement APIs own amounts, currency, current
  holdings, MAX benchmark methodology, lifetime profit and saved assumptions.
  Exact decimal values are copied; no model arithmetic or new valuation is used.
- Explicit documentation clauses use Python RAG. Known personal-value explanations
  become public methodology questions; private snapshots are never sent to that
  documentation route. Unknown questions retain the filtered profile-facts path.
- Explicit quote requests use `ExternalMarketDataService`, including its durable
  cache, attempted-call budget and coverage policy. Only the public symbol leaves
  Java. The largest holding uses its scoped canonical ticker; no listing is guessed.

Response fields are `answer`, actually attributable `sources`, `components`, and
optional `marketData`. Raw provider data stays in `marketData`, separate from
verified answer prose. The panel renders it as untrusted text with cache status,
provider trading date (or unknown), fetch/expiry timestamps and stale warnings.
A fetch timestamp is not a valuation date. Provider absence/quota failure leaves
Java facts available and adds an explicit unavailable/skipped message; it never
invents a quote or adds a successful market citation.

Request validation and strict active membership checks precede source reads.
The route shares existing per-user rate/concurrency admission and always releases
its permit. Basic/bearer read-only requests have the existing narrow CSRF exemption;
browser-cookie requests still require CSRF. Unsupported periods, forecasts and
trading decisions abstain before any source lookup.

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
