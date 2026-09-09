# Dashboard HappyInvestor facts scenario

## Scope

Facts-only browser verification for `/portfolios/{portfolioId}/dashboard` using HappyInvestor
portfolio `2`. This scenario covers rendered Dashboard values and their reporting semantics; it does
not repeat route smoke or active-control coverage.

## Authoritative sources

- Independent expected facts: `HappyInvestorDashboardFacts` and `HappyInvestorTestData`.
- Dashboard composition: `InvestmentDashboardApplicationService` → `InvestmentDashboardFacade`.
- Rendered page: `dashboard.html` and dashboard fragments.
- Existing executable reference: `InvestmentDashboardGoldenUiIT` and dashboard application/service
  contract tests.
- The browser run uses the normal application clock and production date-selection rules. The closed
  fixture's last persisted market/FX observations are data-invariant; the seed pins all canonical
  instruments by symbol so unrelated global cache rows cannot replace them.
- `HappyInvestorBrokerFacts` contains the historical boundary inventory (`2025-12-31`) for source
  reconciliation. It is not a timeless live-page expectation.

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

Use `HappyInvestorDashboardFacts` for expected values and `FinancialPresentation` rules for
rendered precision. Do not compare MAX and YTD values unless the underlying period semantics make
them comparable. A missing performance series is expected only when the canonical reporting facts
also have no series.

### Fact strength and coverage report

The scenario report must name the strength of every checked fact. `CANONICAL` means an exact
expectation independently specified in `HappyInvestorDashboardFacts`; `DERIVED` means an expected
value calculated from canonical source facts and the formula below; `DERIVED-PARTIAL` means the
formula is known but one or more independent source checkpoints are missing; `UNDEFINED` means the
HappyInvestor story does not currently define the value. Do not turn `BLOCKED` or `UNDEFINED` into
passes, and do not use a rendered Dashboard value as an expected fact.

| Fact | Strength | Independent expectation / formula | Tolerance or semantic check |
| --- | --- | --- | --- |
| Balance, deposits, withdrawals, net deposits | `CANONICAL` | `HappyInvestorDashboardFacts` constants | `FinancialPresentation` monetary rounding |
| Open-position value, unrealized P/L, return | `CANONICAL` | `HappyInvestorDashboardFacts` constants | `FinancialPresentation` monetary/percentage rounding |
| AAPL and TSLA value/unrealized P/L | `CANONICAL` | `HappyInvestorDashboardFacts` constants | rendered monetary rounding |
| Equity weight and FX rates | `CANONICAL` | `HappyInvestorDashboardFacts` constants | rendered percentage/rate precision |
| Investment result (`MAX`) | `DERIVED` | `realized P/L + open-position unrealized P/L + dividends + signed withholding tax + interest`, with each source converted to PLN on its transaction/valuation date | `MONEY_TOLERANCE`; do not substitute balance minus net deposits, and keep distinct from realized and unrealized P/L |
| Period return (`MAX`, `YTD`) | `DERIVED` | compound the independently supplied monthly return factors; MAX spans `2024-07-31`–`2025-12-31`, YTD spans `2025-01-01`–`2025-12-31` | `PERCENT_TOLERANCE`; report the exact boundaries and whether the result is unavailable |
| Drawdown | `DERIVED-PARTIAL` | `drawdown(t) = value(t) / runningPeak(t) - 1`; max is the minimum drawdown | assert sign and peak relationship; missing canonical daily checkpoints are reported |
| Realized P/L | `DERIVED-PARTIAL` | closed-trade result + swap + commission, converted on close date | assert period, currency, sign, and no unrealized mixing; missing independent FX checkpoints are reported |
| Dividends | `DERIVED-PARTIAL` | dividend operations minus reversals, converted on operation date; HappyInvestor source includes gross USD `120.00` and withholding tax `-22.80` | assert selected period, reporting currency, FX treatment, and UI rounding; missing independent converted checkpoint is reported |
| Largest holding/concentration | `DERIVED` | `AAPL value / open positions value × 100 = 134551.634160 / 174847.919664 ≈ 76.96%` | `0–100%`, `AAPL` is largest, all seven canonical positions included |
| Performance series | `DERIVED-PARTIAL` | independently generated ordered values with start, transaction boundary, intermediate, and end checkpoints | verify period, labels, ordering, meaningful-point count, endpoints, and trend; report missing canonical checkpoints |
| Benchmark | `UNDEFINED` | identity is configured as `SPY` / S&P 500, but no independent HappyInvestor benchmark checkpoints exist | do not manufacture values; list required boundary/checkpoint fixture |
| Empty/unavailable states | `CANONICAL` | each metric is explicitly numeric, zero, unavailable, empty, or not applicable for both MAX and YTD | `0` is not equivalent to unavailable; verify visible UI state and series semantics |

The report must also state that the existing canonical set was checked: balance, deposits,
withdrawals, net deposits, open-position value/unrealized P/L/return, AAPL and TSLA value/P/L,
equity weight, and both canonical FX rates. For the current scenario, the report must state the
period boundaries (`MAX`: `2024-07-31` through `2025-12-31`; `YTD`: `2025-01-01` through
`2025-12-31`) and distinguish numeric zero, empty, unavailable, and not-applicable outcomes.

### Deposit drill-down checkpoint

Trace the deposit fact through the visible UI and its source operations:

```text
Dashboard card
  -> open info
Deposit popup
  -> individual operations
  -> sum for selected period and reporting currency
  -> 451,127.99
```

For the canonical HappyInvestor story, assert `DEPOSITS = 451127.99` independently from
`HappyInvestorDashboardFacts`, then verify the popup operation rows, selected-period boundaries,
reporting currency, and displayed total `451,127.99`. The popup total must equal the sum of its
included deposit operations; do not accept a copied card value without the operation, period, and
currency checks.

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
continue with all independently verifiable cards, popups, tables, charts, MAX/YTD states, and
responsive checks.

Abort or return `BLOCKED` only when a prerequisite makes the remaining assertions unreliable, such
as the wrong HappyInvestor portfolio or fixture identity.

### Classification rule

- `PASS` — observed value/state agrees with its independent expectation.
- `FAIL` — sufficient independent expectation exists and the product disagrees.
- `SUSPICIOUS` — behavior is questionable but evidence is insufficient to establish a product defect.
- `DERIVED-PARTIAL` — formula/semantics are known but independent source checkpoints are incomplete.
- `UNDEFINED` — HappyInvestor currently has no independent expectation for the metric.
- `BLOCKED` — prerequisite, fixture, or environment prevents reliable verification.

Product mismatch is `FAIL`; insufficient canonical data is `DERIVED-PARTIAL` or `UNDEFINED`; a
wrong fixture is `BLOCKED`. Never convert missing canonical data into `PASS`.

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

`canonical dashboard/reporting fact → DashboardFacade/ApplicationService → rendered card/table/chart`

Reconcile positions to portfolio structure and applicable allocation totals. Preserve distinctions
between balance, invested capital, deposits, withdrawals, unrealized result, realized result,
dividends, and return. Do not use Dashboard REST responses as the expected-value source.

## Responsive/browser health

Run at `1440x1000` and `390x844`. Check visible cards/tables/chart labels, no page overflow or
clipping, page/console errors, failed first-party requests, and no non-GET requests. Do not trigger
Import, Update market data, Export, manual-price Save, or any refresh control.

## Result rules

Use `PASS`, `SUSPICIOUS`, `FAIL`, or `BLOCKED`. A wrong portfolio story is `BLOCKED`; an expected
fact mismatch after the precondition passes is `FAIL` with source, URL, value, and evidence.
