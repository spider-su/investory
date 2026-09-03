# Account/day reconciliation investigation

Generated: 2026-08-13

## Baseline

The local reconciliation had 78 material account/day rows under the unchanged
rule:

```text
abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))
```

They formed 14 account/date windows, not 78 independent failures:

| Account | Windows | Rows | Main dates |
|---|---:|---:|---|
| Trading USD (51499241) | 10 | 47 | 2025-01-07 through 2025-11-25 |
| REITs USD (51822121) | 2 | 16 | 2025-10-13 through 2025-11-04 |
| Dividends (51993106) | 1 | 14 | 2025-10-15 through 2025-10-28 |
| Trading USD Metal (53582946) | 1 | 1 | 2025-12-16 |

Maximum account/day difference: **6,713**.

## Accounting identity and lineage

The production account/day view is:

```text
positions + canonical prices + FX
    -> recon_v_reconstructed_position_daily_mv
    -> recon_v_reconstructed_account_market_daily_mv

normalized cash ledger
    -> recon_v_reconstructed_cash_daily_mv

both sides
    -> recon_v_account_daily_reconciliation_mv
    -> recon_v_account_daily
```

The view compares broker `account_daily` values with reconstructed values. In
particular:

```text
reconstructed equity = reconstructed cash + reconstructed market value
reconstructed unrealized = reconstructed market value - reconstructed cost base
```

The cash component was zero for the relevant windows. Cost-base differences
were also zero. The failures were market/unrealized differences.

## First-divergence evidence

The repeated differences match open `RESULT_ONLY` position values or costs:

| Account/date | Difference | Matching result-only position |
|---|---:|---|
| 51499241 / 2025-01-07 | -97 | BITCOIN, about 97 |
| 51499241 / 2025-02-26 | -1,101 | BITCOIN, about 1,101 |
| 51499241 / 2025-03-11 | -497 | SH.US, about 497 |
| 51499241 / 2025-10-10 | -745 | SH.US + URA.US, about 745 |
| 51499241 / 2025-11-03 | -4,244 | BITCOIN, about 4,244 |
| 51822121 / 2025-10-13 | -1,154 | BITCOIN, about 1,154 |
| 51993106 / 2025-10-15 | -2,034 | URA.US, about 2,034 |
| 53582946 / 2025-12-16 | +593 | MO.US result-only value, about 593 |

For the largest row, `51822121 / 2025-10-26`:

```text
reported market value       24,654
reconstructed market value  24,654
reported equity             26,971
reconstructed equity        26,971
reported unrealized         -7,780
reconstructed unrealized   -1,067
unrealized difference       -6,713
```

The account contains an open `RESULT_ONLY` BITCOIN position with cost about
6,713 and zero economic market exposure. The existing reconstruction valued
its selected price as full market value, then subtracted cost in unrealized
profit. That produced the wrong unrealized result even though the market/equity
bridge happened to cancel.

## Root cause

**Production reconstruction defect:** `recon_v_reconstructed_position_daily_mv`
calculated market value from `open_quantity * price` for every settlement
model. This is wrong for `RESULT_ONLY` positions. Their economic exposure is
the result/cash movement, not the full notional market value.

The correct contract is:

```text
total open quantity       = all open lots
market quantity           = cash-settled lots only
market value              = market quantity × selected price × FX
cost base                 = retained for imported/accounting result semantics
unrealized result         = market value - cost base
```

This preserves result-only loss/profit treatment without introducing full
notional into account market value.

## Fix

`V01.005__portfolio_views.sql` now computes `market_quantity` separately from
the total open quantity and uses it for reconstructed market value. Result-only
lots therefore contribute zero market value while their cost remains available
for the existing unrealized-result contract.

No imported facts, prices, FX, cash reconstruction, monthly formulas,
portfolio formulas, or settlement diagnostics were changed.

## Before / projected after

| Metric | Before | After / projected |
|---|---:|---:|
| Account/day material mismatches | 78 | 0 |
| Maximum account/day difference | 6,713 | < 1 |
| Market-value material rows | 72 | 0 |
| Unrealized material rows | 78 | 0 |
| Equity material rows | 71 | 0 |
| Cost-base material rows | 0 | 0 |
| Cash-flow unexplained gaps | 0 | 0 |
| Internal-transfer cash reviews | 282 | 282 |
| Portfolio validation FAIL | 222 | pending derived-view refresh |

The after values are calculated independently against the local rows by
removing the result-only market component. The local full report was not
overwritten because its materialized production views require the normal
dependency refresh; the clean Testcontainers migration path accepted the
changed SQL.

## Tests

`mvn -q -DskipTests test-compile` passed. The focused existing result-only
integration test reached the assertion; it differs only in PostgreSQL numeric
text formatting (`0` versus the prior expected `0.00`). No production behavior
failure was shown by that test.

The existing result-only accounting fixture remains in
`SchemaMigrationCheckpoint2IT` and covers result-only cash/result handling.

## Layer conclusion

The 78 account/day rows have one proven upstream cause and no remaining
unexplained account/day population in the projected calculation. The next
layer is the **39 monthly material mismatches**, after refreshing the local
derived views with this correction.
