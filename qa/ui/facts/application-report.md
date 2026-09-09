# Investory HappyInvestor Facts coverage report

Status: `REAL-CLOCK VALIDATION IN PROGRESS`; no application clock override is used. Shared-database
isolation and valuation defects are resolved; the closed-fixture price-cache overlay was hardened.

## Browser execution

- Browser: Playwright MCP
- Environment: `http://localhost:8080`
- Portfolio requested: `2`
- Viewports inspected: `1440x1000`, `390x844` where applicable
- Persistent mutations: none
- Console/page errors and failed first-party requests during facts inspection: none
- Non-GET requests: none
- Real-clock Dashboard MAX rendered: balance `231.8K`, AAPL `177.0K`, unrealized P/L `103.9K`.
- Real-clock Dashboard YTD rendered: investment result `-645`, realized P/L `0`, dividends `0`.
- Real-clock Profile rendered: net worth `1.21M`, market projected income `-938`, Long-Term value
  `980.0K`, Long-Term net income `75.0K`.

## Observed routes

- Dashboard: `/portfolios/2/dashboard?period=MAX`, `/portfolios/2/dashboard?period=YTD`
- Profile: `/portfolios/2/investment-profile`
- Portfolio reporting: `/portfolios/2/dashboard/reconciliation`
- Long-Term reference: `/portfolios/2/long-term-assets`

## Fixture precondition

Long-Term Assets matches the HappyInvestor portfolio-2 story: `980.0K` and current forward net
annual income `75,037`, including both Treasury records. The brokerage source rows now have
canonical asset identity, but the reporting side does not yet match canonical HappyInvestor facts:

- Before the seed fix, `asset_id=1251` resolved to `AMZN.US`, producing the observed AMZN balance.
- After the fix, portfolio 2 positions link to canonical AAPL/TSLA/VWRA/NVDA/GOOGL/MSFT and both
  Treasury symbols; the seed postflight reports `CANONICAL_ASSET_LINKS_OK`.
- Treasury 2033 price `98.81` is a percent-of-par quote. Carry-forward rows now preserve that
  convention, so `10,000 * 98.81% * 3.7111 = 36,669.3791 PLN`.
- The shared-database isolation contract confirms unrelated portfolio data does not change
  portfolio-2 KPI/allocation results after a normal reporting refresh.
- The complete broker story is now explicit in `HappyInvestorBrokerFacts`: AAPL, VWRA, NVDA, TSLA,
  GOOGL, MSFT and the matured Treasury at the shared `2025-12-31` boundary.
- Dashboard/Profile facts now reference that independent source inventory. Position values use the
  pinned price observation at or before the boundary and boundary FX (`USD/PLN=3.6016`,
  `EUR/USD=1.173562`). Treasury uses percent-of-par valuation.
- Source-contract tests pass; they do not invoke production Dashboard/Profile code.
- Production Dashboard/Profile use the application clock/current reporting period. Browser facts
  therefore classify MAX/YTD, maturity, current income, stale-price, and carry-forward values as
  date-sensitive and derive them independently.
- The browser instance still rendered the pre-overlay market cache after the Neon seed completed;
  this indicates the running local application is not connected to the seeded database (or its
  reporting cache was not refreshed). The browser result is therefore `SUSPICIOUS`, not a product
  defect conclusion.

Assertions were not weakened and no clock override was introduced. The seed now pins all canonical
market-cache symbols by business identity, so the closed fixture remains deterministic under normal
production date selection.

## Coverage matrix

| Area | Facts coverage | Reconciliation | Cross-page | Result |
| --- | --- | --- | --- | --- |
| Long-Term Assets | STRONG | STRONG | source available | PASS |
| Dashboard | scenario created | source values and identity reconcile at 2025-12-31; fixed-clock browser pending | blocked | BLOCKED |
| Profile / Income | scenario created | source values reconcile with Dashboard and boundary Long-Term facts; fixed-clock browser pending | blocked | BLOCKED |
| Portfolio reporting | scenario created | page is system-wide; C0 reports portfolio-1 import failures | blocked | BLOCKED |
| Investment reporting | scenario created | canonical links and scoped KPI/allocation pass; full old-fact reconciliation blocked | blocked | BLOCKED |

## Canonical/derived/contract/undefined facts

- `CANONICAL`: source instruments, quantities, pinned prices, FX and boundary date in
  `HappyInvestorBrokerFacts`;
- `DERIVED`: Dashboard open-position value `174847.919664 PLN`, no residual portfolio-level
  brokerage cash after the explicit boundary withdrawals, and market portfolio value
  `174847.919664 PLN`;
- `DERIVED`: whole-wealth/profile values in `HappyInvestorProfileFacts` from the same broker
  source plus boundary Long-Term facts;
- `CANONICAL`: reporting checkpoints in `HappyInvestorReportingFacts` and `HappyInvestorDailyFacts`;
- `DERIVED`: compact display values, allocations, combined income, and Long-Term/Profile shared
  values using existing presentation/domain rules;
- `CONTRACT`: Dashboard/Profile read-service composition and existing reporting/profile tests;
- `UNDEFINED`: live Dashboard chart series when the canonical fixture has no series; current
  Reconciliation issue counts for this deployment; Profile Income Base when unavailable.

## Defects and suspicious findings

- Fixed production defect: carry-forward bond prices dropped the percent-of-par quote
  convention, inflating Treasury 2033 valuation by 100x. The generic price-history path now
  preserves the convention and migration `V01.013` repairs existing rows.
- Remaining execution blocker: the live app must be started with
  `investory.time.fixed-instant=2025-12-31T12:00:00Z` before browser values can be compared to
  these boundary facts. The normal application intentionally uses the current clock.
- Reconciliation is visibly `UNRECONCILED`; its current C0 failures are portfolio-1/system-wide
  import failures, not evidence that portfolio 2's source overlay failed.
- Existing bond form presentation rounding remains governed by its dedicated Long-Term scenario;
  this task did not normalize it.

## Recommendation

Keep the shared-database isolation contract. Run the Dashboard/Profile/Long-Term browser Facts
scenarios against a clean database with the supported fixed-clock application configuration, then
repeat the seed for idempotency and optionally verify shared Neon coexistence. Do not mark
Dashboard/Profile `FACTS-STRONG` until the fixed-boundary rendered values reconcile.
