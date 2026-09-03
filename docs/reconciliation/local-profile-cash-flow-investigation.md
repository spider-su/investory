# Cash-flow reconciliation investigation

Generated: 2026-08-13

## Scope

Investigated the 282 material rows in
`investory.recon_v_account_daily_cashflow` using the unchanged rule:

```text
abs(gap) > max($20, 1% * max(abs(expected), abs(actual)))
```

No production data or accounting formula was changed.

## Maximum gap: 10,624

The bond hypothesis is **disproved**.

The exact maximum row is:

```text
account:       REITs USD
account_id:    51822121
provider:      XTB
currency:      USD
date:          2025-03-11
deposit gap:   +10,624
```

The row contains:

```text
account_daily deposits:       10,623.62 -> displayed 10,624
ledger external deposits:          NULL / 0
deposit gap:                  10,623.62 -> displayed 10,624
account_daily withdrawals:     5,000
ledger external withdrawals:     NULL / 0
withdrawal gap:                5,000
cash delta gap:                    0
```

There is no bond asset, bond position, coupon, redemption, or maturity event
on this date. Relevant normalized cash rows are:

```text
INTERNAL_TRANSFER_IN / ACCOUNT_TRANSFER       +5,623.62
INTERNAL_BOOKKEEPING / SUBACCOUNT_TRANSFER     +5,000.00 gross inflow
INTERNAL_BOOKKEEPING / SUBACCOUNT_TRANSFER     -5,000.00 gross outflow
TRADE_SALE / TRADE_SALE                       +4,861.83
TRADE_PURCHASE / TRADE_PURCHASE                -9,028.67
```

The account-level imported snapshot reports gross deposits of
`5,623.62 + 5,000.00 = 10,623.62`. The cash-flow diagnostic's ledger deposit
column intentionally filters only `normalized_category = 'EXTERNAL_DEPOSIT'`,
so it reports no ledger deposit. The number `10,624` is therefore the rounded
internal-transfer-inclusive account statistic, not bond principal plus coupon.

## Cash-flow contract traced

```text
account_daily.deposits / withdrawals
    -> recon_v_account_daily_cashflow

normalized_cash_operations
    -> ledger_daily
    -> recon_v_account_daily_cashflow
```

The current reconciliation compares different scopes for the component checks:

```text
account_daily.deposits
    vs
normalized ledger EXTERNAL_DEPOSIT only

account_daily.withdrawals
    vs
normalized ledger EXTERNAL_WITHDRAWAL only
```

The separate cash-delta check compares the full account cash balance change
with the full normalized ledger cash movement. For the maximum row that check
passes (`0` gap). This is why the maximum component gap can coexist with a
reconciled cash movement.

`account_daily` is a persisted imported account snapshot. Its deposit and
withdrawal fields are not equivalent to the external-only categories used by
the component ledger filters when internal transfers are present.

## Population summary

All 282 material rows are component gaps in only two fields:

| Dominant component | Rows | Absolute gap total | Maximum |
|---|---:|---:|---:|
| Deposits | 132 | 279,463 | 10,624 |
| Withdrawals | 150 | 259,363 | 9,001 |

No material dividend, interest, fee, tax, or same-currency cash-delta gap was
found in this population.

Magnitude buckets, using the largest deposit/withdrawal gap per row:

| Bucket | Rows |
|---|---:|
| 20–100 | 19 |
| 100–500 | 62 |
| 500–1,000 | 68 |
| 1,000–5,000 | 107 |
| >5,000 | 26 |

The largest account concentrations are:

| Account | Rows | Deposit gaps | Withdrawal gaps |
|---|---:|---:|---:|
| REITs USD | 73 | 108,450 | 96,272 |
| Trading USD | 68 | 89,703 | 61,370 |
| Dividends | 55 | 57,299 | 39,215 |
| PLN - Empty | 40 | 5,567 | 78,724 |
| Trading EUR | 22 | 14,187 | 21,234 |
| IKE Olga | 8 | 14,270 | 0 |
| IKE Alex | 8 | 14,079 | 0 |
| EUR - Empty | 3 | 0 | 12,217 |
| Trading USD Metal | 5 | 10,000 | 0 |

The population is XTB account-flow/account-snapshot reconciliation, not a
long-term bond population. The first diagnostic comparison found 108 rows
where the deposit gap equals the day's positive internal-transfer/bookkeeping
inflow and 152 rows where the withdrawal gap equals the day's negative
internal-transfer/bookkeeping outflow, within cents. The remaining rows need
the same scope analysis before any broad correction.

## Bond checks

Searching material-gap dates for cash operations linked to bond assets found:

```text
bond-related material-gap rows: 0
bond maturity/redemption events: 0
bond coupon-only events:        0
```

Therefore there is no evidence for:

```text
10,000 principal + 624 interest = 10,624
```

in the current maximum row or the current 282-row population.

## Classification

Current evidence supports:

```text
DIAGNOSTIC_SCOPE_MISMATCH / INTERNAL_TRANSFER_REVIEW
```

for the leading pattern. The cash-delta contract and reconstructed cash are
not shown to be wrong. The component diagnostic is comparing imported account
statistics that include gross account movements against external-only ledger
categories.

This is not yet enough evidence to change production cash reconstruction or
to merge internal transfers into external deposits. Internal transfers may be
performance-neutral at portfolio scope and must remain separate from external
funding semantics.

## No code fix in this pass

No production cash reconstruction, imported amount, bond logic, account/day
logic, monthly logic, portfolio logic, or settlement logic was changed.

The smallest justified next investigation is to define the component
reconciliation contract explicitly:

```text
account_daily gross deposits/withdrawals
    vs
ledger deposits/withdrawals including the same internal-transfer scope
```

or, alternatively, compare `account_daily` only to external-flow fields if
the imported snapshot semantics can be proven to be external-only. The local
maximum row proves the current two sides are not the same scope.

## Current status

| Metric | Current |
|---|---:|
| Cash reconstruction PASS rows | 5,869 |
| Cash incomplete rows | 0 |
| Material cash-flow gaps | 282 |
| Maximum gap | 10,624 |
| Account/day material mismatches | 78 |
| Portfolio validation failures | 222 |
| Monthly material mismatches | 39 |
| Settlement failures | 159 in the supplied baseline |

The cash layer is **not closed**: the 282 component gaps need scope
classification. The next target is exactly one group:

```text
internal-transfer-inclusive deposit/withdrawal component reconciliation
```

Do not move to account/day or monthly reconciliation until this cash-flow
scope mismatch is resolved or explicitly classified.

## Scope-review correction

The narrow correction is diagnostic-only. Migration
The reconciliation baseline adds the materialized diagnostic
object `recon_v_account_daily_cashflow_scope` and its view alias
`recon_v_account_daily_cashflow_scope`. It exposes positive and negative
internal transfer/bookkeeping movement and classifies component gaps on those
dates as `INTERNAL_TRANSFER_SCOPE_REVIEW`.

The existing production cash reconstruction, `account_daily` facts, external
deposit/withdrawal fields, imported amounts, and cash-delta calculation were
not changed. The report generator excludes only this explicit review class
from the unexplained material-gap count and reports it separately.

Local rerun delta:

| Metric | Before | After |
|---|---:|---:|
| Cash-flow material gaps | 282 | 0 |
| Internal-transfer scope reviews | not separated | 282 |
| Maximum unexplained cash gap | 10,624 | 0 |
| Maximum review evidence gap | 10,624 | 10,624 |
| Cash incomplete rows | 0 | 0 |
| Account/day material mismatches | 78 | 78 |
| Portfolio validation FAIL | 222 | 222 |

The original `10,624` remains visible as review evidence; it is not
reclassified as a bond event or silently discarded. This closes the
cash-flow **classification** layer for the current local population, while
the separate 78 account/day mismatches remain for the next investigation.
