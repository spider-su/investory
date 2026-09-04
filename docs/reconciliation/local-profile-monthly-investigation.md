# Monthly reconciliation investigation

Generated: 2026-08-13 from the refreshed local database.

## Scope and materiality

The unchanged rule is:

```text
abs(difference) > max($20, 1% * max(abs(expected), abs(actual)))
```

The refreshed `account_monthly_mv` and `portfolio_monthly_mv` report an
`updated_at` of `2026-08-13 11:23:03+02`.

Current monthly result:

| Result | Rows |
|---|---:|
| `MISMATCH` | 40 |
| `OK` | 164 |
| Material mismatches | 39 |
| Maximum material difference | 10,323 |
| Total absolute material difference | 109,846 |

The one additional `MISMATCH` row is below the materiality rule.

## Data path

The canonical monthly profit path is:

```text
account_daily.daily_profit_amount
  -> app_v_account_monthly.monthly canonical_profit
  -> recon_v_account_monthly_profit
  -> recon_v_account_monthly_profit
  -> dashboard monthly performance / reconciliation report
```

`account_monthly_mv` converts account-daily monetary values using the
snapshot-date FX rate and sums `daily_profit_amount` by account and month.
The reconciliation view independently computes:

```text
expected_boundary_profit =
    closing_equity
  - opening_equity
  - account_daily deposits
  + account_daily withdrawals

difference = canonical_profit - expected_boundary_profit
```

The monthly dashboard value is the canonical sum of daily profit. The
boundary expression is a diagnostic comparison, not the source of the
dashboard monthly profit.

## Population and first-divergence pattern

The 39 material rows are concentrated in recurring account/month records,
not a single broken monthly aggregation. Main concentrations are:

| Account | Material rows | Largest difference |
|---|---:|---:|
| `51993106` Dividends | 14 | 3,089 |
| `51822121` REITs USD | 11 | 6,560 |
| `51499241` Trading USD | 9 | 10,323 |
| `51548444` Trading EUR | 2 | 5,223 |
| `50290466` PLN - Empty | 3 | 2,404 |
| total | 39 | 10,323 |

The earliest representative divergence is account `51499241`, January 2025:

```text
canonical daily-profit sum       618.15
boundary calculation          10,524.00
diagnostic difference         -9,906.00
```

The same account then alternates between similarly large positive and
negative boundary differences as account deposits/withdrawals change. This
is not a stale single opening balance: the monthly views were refreshed and
the differences track the flow fields used by the boundary formula.

## Root cause

**Diagnostic contract defect / scope mismatch.** No monthly `daily_profit`
calculation defect was demonstrated.

`account_daily.deposits` and `account_daily.withdrawals` are not a stable
canonical flow contract for this account-level boundary. They combine broker
and internal movement semantics. For example, in
account `51499241`, January 2025 contains:

```text
account_daily deposits       24,379.09
account_daily withdrawals    10,861.59
net account_daily flow       13,517.50

normalized cash ledger:
internal transfer in         21,237.09
internal transfer out          -852.00
external deposits             3,043.00
external withdrawal              -5.00
```

The account-daily fields therefore cannot be used as a direct proxy for the
normalized account-flow ledger. Using them in:

```text
closing equity - opening equity - deposits + withdrawals
```

creates the observed false monthly mismatch. It also explains the recurring
round-number differences and their sign changes around flow-heavy months.

The documented accounting contract keeps external funding, internal
transfers, and investment result separate. At account scope, both external
funding and internal transfers are balance movements and must be included in
the equity boundary; neither is investment profit. At portfolio scope, paired
internal transfers net to zero. The canonical replacement is therefore
normalized cash operations where `is_external_flow OR is_internal_transfer`,
with trade cash, fees, interest, and result rows excluded from the boundary
flow input.

## What was and was not changed

No source facts or monthly formulas were changed in this verification pass.
No production dashboard value was changed. The refreshed materialized views
confirm that the existing canonical monthly profit remains the sum of daily
profit, while the separate boundary diagnostic remains incompatible for
these rows.

The local refresh was not complete for the preceding account/day correction:
`recon_v_account_daily_reconciliation_mv` still contains result-only market-value
failures (for example account `51499241` on `2025-11-03`, difference `-4,244`).
Its source materialized view chain needs the normal dependency-order refresh
before the projected account/day result can be verified. This does not alter
the monthly finding: `account_monthly_mv` and the monthly reconciliation view
were refreshed at `2026-08-13 11:23:03+02`, and the 39 monthly rows are
present in that refreshed state.

The smallest correct change is a **monthly reconciliation-only** fix using
that normalized non-performance account-flow scope. It does not alter
`account_daily`, imported facts, or the dashboard monthly P/L.

## Regression invariant

For every account/month:

```text
canonical monthly profit
  = SUM(account_daily.daily_profit_amount converted with snapshot-date FX)
```

The independent boundary check should only be marked comparable when its
opening/closing equity and flow inputs use the same account and currency
scope. Internal transfers must affect account-level equity where applicable,
but must not be silently treated as external investment funding.

## Layer conclusion

The refreshed views did **not** close the monthly diagnostic population:
39 material rows remain. The canonical monthly P/L path is identifiable and
the mismatch is upstream in the boundary diagnostic's flow semantics.

No monthly production fix was applied because the exact replacement boundary
flow contract needs to be selected and regression-tested first. The next
focused implementation should correct this reconciliation view only, then
rerun the full report before investigating downstream portfolio results.
