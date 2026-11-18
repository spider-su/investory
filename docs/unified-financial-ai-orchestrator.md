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

## Bounded follow-ups

Ask for portfolio profit or a SPY comparison in one supported period. For an
asset, use “Show realized profit for asset MSFT.US for MAX”. A successful
single report returns a `context` object with `portfolioId`, `topic`, optional
`asset`, and `period`. The panel keeps it in memory for one follow-up and
clears it when closed or when the next request starts. API clients echo the
object in the next request alongside the question.

“Compare that with YTD” reads the original and target period again and returns
both labeled reports. “Show that for YTD” reads only the target period.
Every request repeats portfolio authorization and checks that the context
matches the URL. Follow-ups bypass the portfolio and benchmark calculation
caches while using the same canonical reporting data. Previous answer values
never enter calculation or storage.
Missing context prompts the user to select a report. Unsupported follow-up
wording abstains. Composite answers and public quotes do not establish report
context. The route uses existing Java reporting reads directly.

## Synthetic acceptance

`app/src/test/resources/fixtures/assistant/questions.json` contains reusable
questions and expected sources, routes, or abstentions. The registered
`FinancialAiOrchestratorIT` runs them against disposable PostgreSQL and
checks numeric facts against canonical Java profile data. RAG and external
responses are deterministic fixtures. Additional cases verify stale-context
isolation, revoked membership, asset period comparison, and metric exposition.

## Monitoring

Java records fixed outcomes and routes through Micrometer. Prometheus can
scrape `/actuator/prometheus` when `INVESTORY_MANAGEMENT_ENDPOINTS=health,prometheus`
is set. Use authenticated collection over the private network. The endpoint
requires the administrator role and is disabled by the default exposure list.
Production collector wiring is separate from the existing private Python RAG
scrape.

| Prometheus series | Dimensions |
| --- | --- |
| `investory_assistant_routes_total` | `route` such as `JAVA_PROFILE`, `JAVA_ASSET` or `ABSTAINED` |
| `investory_assistant_request_seconds_bucket` | `outcome` and bucket boundary |
| `investory_market_events_total` | `event` such as `CACHE_HIT`, `ATTEMPT`, `BUDGET_EXHAUSTED` or `PROVIDER_FAILURE` |
| `investory_market_provider_seconds_bucket` | `outcome` and bucket boundary |

Example queries for sustained failures, cache use, and assistant latency:

```promql
increase(investory_market_events_total{event=~"PROVIDER_FAILURE|PROVIDER_RATE_LIMIT|BUDGET_EXHAUSTED"}[15m])
sum by (event) (rate(investory_market_events_total{event=~"CACHE_HIT|CACHE_MISS"}[5m]))
histogram_quantile(0.95, sum by (le) (rate(investory_assistant_request_seconds_bucket[5m])))
```

Metrics carry no question text, amounts, tickers, portfolio IDs, user IDs or
provider payloads. Market events count gateway work, so coalesced callers share
one provider attempt and one provider latency observation.

See the [market policy](../services/rag/README.md#durable-market-policy-java-gateway)
and [MCP contract](../adapters/mcp/README.md) for the canonical underlying limits.
Release gates require exact-head CI, scanned Java/RAG publication, runtime image-ID
matching, authenticated API/official-MCP acceptance and a fresh private metrics
scrape. A merged PR alone is not deployment evidence.
