# Long-Term Assets facts scenario

## Required sources

- [`Long-Term Assets domain contract`](../../../docs/domain/long-term-assets.md)
- [`Long-Term Assets manual QA`](../../../docs/quality/02-long-term-assets-manual-qa.md)
- [`HappyInvestor scenario README`](../../../test-support/src/main/java/com/smartbox/investory/testsupport/happyinvestor/README.md)
- `HappyInvestorLongTermFacts` and `HappyInvestorTestData`
- `HappyInvestorReadOnlyUiIT`, `ProfilePersistedFactsIT`, and relevant Long-Term economics tests

## HappyInvestor facts

Confirm the target fixture before checking values. For canonical boundary data, verify collapsed
category summaries, expanded rows, every seeded asset, and representative detail/edit fields.
Cross-check page summary ↔ category ↔ asset row ↔ detail/edit representation ↔ independent facts.

Expected canonical identities are Apartment A, Apartment B, Treasury 2026, the post-reinvestment
Treasury where present, Cash reserve, Term cash reserve, and Family Car. The source facts define exact
values, dates, rates, tax bases, income, and maturity behavior; read them rather than copying numeric
literals into this scenario.

## Reconciliation

Verify category totals and overall Long-Term Assets total, annual-income reconciliation, yield/tax/
coupon/maturity semantics, personal-asset non-investment labeling, PLN/currency behavior, and the
distinction between fixed historical/boundary facts and current date-sensitive Treasury behavior.
Check domain precision against UI presentation rounding and sensible money, percentage, date, zero,
sign, and period labels. Do not treat a REST response or rendered page as the expected-value source.
Bond source facts retain exact coupons of `4.625%` and `4.375%`; the edit-form percentage input is
a two-decimal presentation field and is expected to render them as `4.63%` and `4.38%`.

## Evidence

Record missing/mismatched values with URL/context, expected source fact, observed text, and concise
evidence. Use `2560x1440` only and record obvious clipping or overlap only. Classify as `PASS`,
`EXPECTED ROUNDING / LIVE DATA DRIFT`, `STALE CONTRACT / TEST DEFECT`, `PRODUCT DEFECT`, `BUILD
DEFECT`, `ENVIRONMENT BLOCKER`, or `SUSPICIOUS / NEEDS RECONCILIATION`; reconcile the source ledger
before declaring a financial product defect. Missing
Alternate viewport and breakpoint coverage are out of scope.
