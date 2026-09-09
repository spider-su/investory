# Dashboard HappyInvestor facts scenario

## Scope

Facts-only browser verification for `/portfolios/{portfolioId}/dashboard` using HappyInvestor
portfolio `2`. This scenario covers rendered Dashboard values and their reporting semantics; it does
not repeat route smoke or active-control coverage.

## Authoritative sources

- Independent expected facts: `HappyInvestorDashboardFacts` and `HappyInvestorTestData`. Unit and IT
  tests intentionally use the fixed disposable `test-fast` fixture checkpoint `2025-12-31`. For a
  live browser backed by Neon, the same facts remain valid after that checkpoint while no trades,
  cash operations, market-price updates, or FX updates occurred; this is the observation-frozen
  rule. Reconcile that freeze state with live Neon before assertions.
- Dashboard composition: `InvestmentDashboardApplicationService` → `InvestmentDashboardFacade`.
- Rendered page: `dashboard.html` and dashboard fragments.
- Existing executable reference: `InvestmentDashboardGoldenUiIT` and dashboard application/service
  contract tests.
- The browser run uses the normal application clock and production date-selection rules. The browser
  endpoint may be `http://localhost:8080` while its datasource is Neon; record those identities
  separately. The closed
  fixture's last persisted market/FX observations are data-invariant; the seed pins all canonical
  instruments by symbol so unrelated global cache rows cannot replace them.
- `HappyInvestorBrokerFacts` contains the source inventory and its last-observation boundary for
  reconciliation. It is not a timeless live-page expectation: a new market price changes open
  position value, unrealized P/L, return, concentration, and allocation; a new FX observation also
  changes reporting-currency conversions. New trades or cash operations change the applicable
  source/activity facts and period totals.

Financial source-of-truth rule: the canonical HappyInvestor story is the primary semantic source
for this scenario. Metric definitions come from the [`portfolio accounting contract`](../../../docs/domain/portfolio-accounting.md),
the [`reporting pipeline contract`](../../../docs/architecture/reporting-pipeline.md), and tested
reporting contracts. Do not redefine Income Base, investment result, TWR, XIRR, deposits,
withdrawals, net worth, market/Long-Term income, allocation, cash, or invested capital here.
External deposits and withdrawals follow those contracts, including applicable flow treatment.
For Profile reconciliation, Income Base follows the linked reporting contract. Do not restate its
formula, substitute the current Dashboard balance, or infer the value from the label.

## Preconditions

Confirm the page is the canonical HappyInvestor brokerage story before facts assertions. The visible
portfolio must contain the canonical HappyInvestor instruments and account/reporting identity from
the fact classes. If another portfolio story is rendered, return `BLOCKED` with the observed marker;
do not reinterpret it as an application defect.

## Facts to verify

At `MAX` and `YTD`, verify the current rendered cards and structures where facts exist. Derive
date-sensitive expectations independently from the real current date:

- selected period and portfolio identity in `#dashboard-page-data`;
- balance/cash and invested/deposit/withdrawal cards;
- investment result, period return, drawdown, realized P/L, dividends, and unavailable states;
- open-position value, unrealized P/L, largest holding, concentration, allocation, and account
  currencies;
- performance table/chart series, labels, period, benchmark, and empty-state semantics when the
  canonical story has no series.

Use `HappyInvestorDashboardFacts` for expected values only after the fixture/source/reporting
consistency check and use `FinancialPresentation` rules for rendered precision. Do not compare MAX
and YTD values unless the underlying period semantics make them comparable. A fixed-fixture/live-Neon
mismatch is a stale-expectation or data-boundary finding until reconciled. A missing performance
series is expected only when the canonical reporting facts also have no series.

For Dashboard Performance, wait for portfolio performance data to be populated and benchmark state
to be resolved before evaluating values. Accept populated benchmark data or an explicit authoritative
unavailable state; an interim `—`, empty series, or loading state is not final.

### Fact strength and coverage report

The scenario report must name the strength of every checked fact. `CANONICAL` means an exact
expectation independently specified in `HappyInvestorDashboardFacts`; `DERIVED` means an expected
value calculated from canonical source facts under an existing tested contract; `DERIVED-PARTIAL`
means the contract is applicable but one or more independent source checkpoints are missing; `UNDEFINED` means the
HappyInvestor story does not currently define the value. Do not turn `BLOCKED` or `UNDEFINED` into
passes, and do not use a rendered Dashboard value as an expected fact.

| Fact | Strength | Independent expectation / source | Tolerance or semantic check |
| --- | --- | --- | --- |
| Balance, deposits, withdrawals, net deposits | `CANONICAL` | `HappyInvestorDashboardFacts` constants | `FinancialPresentation` monetary rounding |
| Open-position value, unrealized P/L, return | `CANONICAL` | `HappyInvestorDashboardFacts` constants | `FinancialPresentation` monetary/percentage rounding |
| AAPL and TSLA value/unrealized P/L | `CANONICAL` | `HappyInvestorDashboardFacts` constants | rendered monetary rounding |
| Equity weight and FX rates | `CANONICAL` | `HappyInvestorDashboardFacts` constants | rendered percentage/rate precision |
| Investment result, period return, drawdown, realized P/L, dividends, concentration, and performance series | `DERIVED` / `DERIVED-PARTIAL` | Canonical source facts reconciled under the linked domain/reporting contracts and owning tested checkpoints | Preserve period, scope, currency, flow, FX, unavailable, and presentation rules; report missing independent inputs |
| Benchmark | `UNDEFINED` | No independent HappyInvestor benchmark checkpoints; identity only is configured as `SPY` / S&P 500 | Do not manufacture values |
| Empty/unavailable states | `CANONICAL` | each metric is explicitly numeric, zero, unavailable, empty, or not applicable for both MAX and YTD | `0` is not equivalent to unavailable; verify visible UI state and series semantics |

The report must also state that the existing canonical set was checked: balance, deposits,
withdrawals, net deposits, open-position value/unrealized P/L/return, AAPL and TSLA value/P/L,
equity weight, and both canonical FX rates. For the current scenario, the report must state the
source/activity and last-observation boundary separately from the live YTD boundary, which must be
derived from the actual application clock as the current calendar year's start and the production
YTD end boundary. A `2025-12-31` checkpoint is historical fixture evidence unless the live source
and observation state are unchanged since that checkpoint. Distinguish numeric zero, empty,
unavailable, and not-applicable outcomes.

### Deposit drill-down checkpoint

Trace the deposit fact through the visible UI and its source operations:

```text
Dashboard card
  -> open info
Deposit popup
  -> individual operations
  -> reconcile under the reporting contract for the selected period and reporting currency
```

For the fixed canonical HappyInvestor fixture, assert the canonical `DEPOSITS` fact independently
from `HappyInvestorDashboardFacts`, then verify the popup operation rows, selected-period
boundaries, and displayed total. On a live Neon-backed page, first verify that
the same source operation set and period apply. If no deposit popup exists, record the drill-down as
not provided; do not invent a popup or accept a copied card value as reconciliation.

## Complete Dashboard inventory and drill-down rule

Before evaluating facts, inventory every currently rendered financial card, KPI, table, chart,
informational icon, tooltip, popup, modal, disclosure, and details surface on the Dashboard.

For each financial item record:

- metric/widget name;
- MAX/YTD applicability;
- rendered state: numeric, zero, unavailable, empty, or not applicable;
- fact strength: `CANONICAL`, `DERIVED`, `DERIVED-PARTIAL`, or `UNDEFINED`;
- whether an informational drill-down exists;
- whether that drill-down was exercised;
- final result.

Do not silently omit a rendered financial metric because no explicit subsection exists in this
scenario.

Use the Deposit popup as the reference reconciliation pattern for every discovered financial
informational drill-down:

`canonical/derived source facts → expected detail rows → popup/detail total → parent Dashboard card`

Where mathematically applicable, verify all four layers. If the popup is explanatory rather than
compositional, verify that its labels, period, currency, values, and financial semantics agree with
the parent metric instead of requiring its rows to sum to the card. Do not invent or require
popups that the current Dashboard does not provide.

### Continue-after-failure rule

A failure in one financial metric must not abort independent fact checks. Record the failure and
  continue with all independently verifiable cards, popups, tables, charts, and MAX/YTD states.

Abort or return `BLOCKED` only when a prerequisite makes the remaining assertions unreliable, such
as the wrong HappyInvestor portfolio or fixture identity.

### Classification rule

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. Keep fact
strength (`CANONICAL`, `DERIVED`, `DERIVED-PARTIAL`, and `UNDEFINED`) separate from result category.
Insufficient canonical data is not a pass; a wrong fixture or unavailable prerequisite is an
environment blocker. Do not downgrade the whole Dashboard because an optional or out-of-scope
check could not run.

### Failure evidence

For every mismatch capture:

- metric;
- URL and selected period;
- expected value/state;
- rendered value/state;
- absolute or percentage difference where meaningful;
- `CANONICAL` or `DERIVED` source;
- formula where derived;
- popup/detail evidence when applicable;
- likely layer: fixture, accounting, valuation, reporting, application service, presentation, or unknown.

### Final coverage summary

End the run with:

```text
financial metrics discovered: X
financial metrics checked: X/Y
canonical facts: passed X/Y
derived facts: passed X/Y
derived-partial: X
undefined: X
drill-downs discovered: X
drill-downs checked: X/Y
MAX checks: X/Y
YTD checks: X/Y
failures: X
suspicious: X
blocked: yes/no
```

Also provide this compact matrix:

| Metric | MAX | YTD | Strength | Drill-down | Result |
| --- | --- | --- | --- | --- | --- |
| … | … | … | … | … | … |

The run is complete only when every discovered Dashboard financial metric has an explicit row in
this matrix, including `UNDEFINED` and unavailable metrics.

## Reconciliation

Trace each asserted value as:

`canonical source operation/position → Neon reporting result → DashboardFacade/ApplicationService → rendered card/table/chart`

For `test-fast`, the canonical source is the persisted snapshot overlay. For a local app backed by
Neon, the live Neon source and reporting result are the applicable intermediate layers; localhost
does not imply a local database.

Reconcile positions to portfolio structure and applicable allocation totals. Preserve distinctions
between balance, invested capital, deposits, withdrawals, unrealized result, realized result,
dividends, and return. Do not use Dashboard REST responses as the expected-value source.

## Browser/runtime health

Run at `2560x1440`. Check visible cards/tables/chart labels, obvious clipping or overlap, page/console
errors, failed first-party requests, and no non-GET requests. Do not trigger
Import, Update market data, Export, manual-price Save, or any refresh control.

## Result rules

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. A wrong
portfolio story or unavailable prerequisite is blocked and reported separately. Reconcile the
source ledger before declaring a financial product defect.
