# Profile and Income HappyInvestor facts scenario

## Scope

Facts-only browser verification for `/portfolios/{portfolioId}/investment-profile` using HappyInvestor
portfolio `2`. This scenario covers whole-wealth composition and income semantics, not generic smoke
or active controls.

## Authoritative sources and flow

The controller loads `ProfileClient`, `InvestmentDashboardClient`, and retirement annual cost, then
builds `InvestmentProfilePageView`. The intended flow is:

`ProfileSnapshotReader → InvestmentProfile → InvestmentProfileController/PageView → rendered Profile`

Dashboard performance/YTD values are supplied by the investment reporting client; Profile does not
reimplement investment calculations. Expected facts come from `HappyInvestorProfileFacts`,
`HappyInvestorLongTermFacts`, existing `ProfilePersistedFactsIT`, and the relevant investment
reporting contracts.

The browser run uses the normal application clock. `HappyInvestorBrokerFacts` remains the
historical source inventory at `2025-12-31`, but live Profile expectations must derive current
date-sensitive values from the closed fixture and production rules. The reinvested Treasury is
absent before `2026-03-01` and active after acquisition; the matured Treasury contributes no
forward income after `2026-02-28`.

Financial source-of-truth rule: the canonical HappyInvestor story supplies deterministic source
facts. Metric meaning comes from [`portfolio accounting`](../../../docs/domain/portfolio-accounting.md)
and the [`reporting pipeline`](../../../docs/architecture/reporting-pipeline.md), plus their
tested reporting contracts. This scenario must not redefine Income Base, investment result, TWR,
XIRR, deposits, withdrawals, net worth, income, yield, allocation, cash, or invested capital.
External deposits and withdrawals follow those contracts, including applicable flow treatment.
Income Base is defined by the linked reporting contract. Do not restate its formula, substitute the
current balance, or infer its meaning from the label.

## Preconditions

Confirm canonical HappyInvestor portfolio identity and brokerage markers before asserting market
facts. A different portfolio story is `BLOCKED`, not a Profile defect.

## Facts to verify

Verify rendered values where the closed fixture supports them. Use exact data-invariant facts where
applicable and independently derive current-date/period-sensitive values:

- reporting currency and total net worth;
- market portfolio value and long-term asset value;
- liquid and illiquid composition;
- market, long-term, and combined annual income;
- market/long-term/combined net yield;
- market income YTD and investment result YTD;
- Income Base when available;
- projected annual income and planned long-term income-to-date/progress;
- annual cost and its planned/current-year label;
- allocation rows, horizons, liquidity labels, percentages, and classified-total reconciliation.

Keep these concepts separate:

- current forward Long-Term run-rate;
- projected annual income;
- market annual income;
- actual YTD income/result;
- planned Long-Term income-to-date.

Validate Income Base against the HappyInvestor story, source-operation ledger, and canonical
reporting contract, including applicable external deposits and withdrawals. Use contract-owned
checkpoints when available; if an independent checkpoint is unavailable, classify the result as
`SUSPICIOUS / NEEDS RECONCILIATION` rather than substituting a rendered value.

Do not require equality between them unless the model explicitly defines the relationship.

## Reconciliation

Verify only relationships defined by the linked domain/reporting contracts and the independent
HappyInvestor facts. Do not create local formulas for net worth, income, yield, allocation, cash,
or invested capital. Apply the existing Long-Term facts scenario and UI presentation precision
rules where those contracts require them.

If Income Base, month-weighted annualization, or projected/YTD progress is unavailable in the
canonical fixture, record `UNDEFINED` rather than deriving a new number in this scenario.

## Browser/runtime health

Run at `2560x1440`. Verify income cards, allocation table, labels, and no obvious clipping or
overlap. Attach listeners before navigation. Do not mutate Profile or trigger refresh,
reconciliation, or plan actions.

## Result rules

Use `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT
DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`. Preserve
exact source precision separately from compact rendered values and reconcile the source ledger
before declaring a product defect.
