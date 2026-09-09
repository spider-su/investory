# Portfolio and investment reporting HappyInvestor facts scenario

## Scope

Facts-only browser verification for the portfolio reporting surfaces:

- `/portfolios/{portfolioId}/dashboard`
- `/portfolios/{portfolioId}/dashboard/reconciliation`
- investment asset detail routes when a reporting fact is represented there.

Do not repeat Dashboard smoke or generic active checks here. Focus on reporting facts and pipeline
meaning.

## Authoritative sources

- Dashboard reporting: `InvestmentDashboardFacade`, `PortfolioMetricsService`,
  `PortfolioPerformanceQuery`, and dashboard reporting model contracts.
- Reconciliation: `ReconciliationController` and the investment reconciliation report contract.
- Canonical facts: `HappyInvestorDashboardFacts`, `HappyInvestorProfileFacts`,
  `HappyInvestorReportingFacts`, `HappyInvestorDailyFacts`, and existing reporting integration
  tests.
- Supporting lineage: accounting/ledger → account daily/reporting views → read model → rendered
  page. Database evidence supports expected facts; Playwright verifies the UI.

Financial source-of-truth rule: use the canonical HappyInvestor story for deterministic facts and
the linked [`portfolio accounting contract`](../../../docs/domain/portfolio-accounting.md),
[`reporting pipeline contract`](../../../docs/architecture/reporting-pipeline.md), and tested
reporting contracts for metric meaning. Do not redefine Income Base, investment result, TWR, XIRR,
net worth, income, allocation, cash, or invested capital in this scenario. External deposits and
withdrawals must be reconciled according to the existing contract, including applicable flow
treatment.

## Preconditions

Confirm portfolio 2 is the canonical HappyInvestor reporting story, including account membership,
instrument identity, reporting currency, and as-of period. A different story is `BLOCKED`.

## Facts to verify

For the Dashboard reporting view, verify only facts present in the canonical classes:

- portfolio/balance and invested capital;
- deposits and withdrawals;
- position value and unrealized/realized result;
- income/dividends and return/yield where available;
- allocation, account currency, account/portfolio breakdown;
- period labels and MAX/YTD semantics;
- benchmark/series values or an explicitly supported unavailable state.

For Reconciliation, verify the rendered scope, as-of date, checkpoint identity, status labels, and
read-only diagnostic semantics. Existing canonical reporting facts or contract tests may establish
intermediate checkpoint expectations. Do not invent expected issue counts for a live diagnostic.
If the page reports an environment/import inconsistency, classify it as `ENVIRONMENT BLOCKER` or
`SUSPICIOUS / NEEDS RECONCILIATION` until the fixture source is confirmed.

For tables, reconcile row identity, date/period, currency, displayed totals, and ordering only when
the domain contract defines the relationship. Distinguish unavailable, blocked, zero, and not-applicable.
Keep fixed HappyInvestor facts and contract-derived reporting values separate from live Yahoo prices,
FX, and other non-deterministic observations.

## Date and precision rules

Label each expected value as timeless/static, as-of-date, period-based, YTD, current forward run-rate,
or historical boundary fact. Keep exact domain values separate from UI compact/rounded values. The
Long-Term Treasury maturity rule does not automatically apply to investment-reporting YTD values.

## Browser/safety

Run Dashboard at `MAX` and `YTD`, Reconciliation at its current as-of state, and applicable asset
detail routes at `2560x1440`. Monitor console, page errors,
failed first-party requests, and non-GET requests. Never press Recheck now, Import, Update market
data, Export, manual-price Save, or any other state-changing control.

For Dashboard Performance, wait for portfolio performance data and benchmark state to settle before
checking values. Accept populated benchmark data or an explicit authoritative unavailable state; an
interim `—`, empty series, or loading state is not a final result.

## Result rules

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. A wrong
story or missing canonical expected fact is blocked or suspicious; reconcile the source ledger
before declaring a product defect.
