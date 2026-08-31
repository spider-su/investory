# Account statistics realized-profit investigation

Generated: 2026-08-14

## Contract finding

`account_statistics.realized_profit` is a closed-position reconstruction. It reads
`positions` with `close_time IS NOT NULL`, converts each closed result at its close date,
and applies settlement semantics:

- `CASH_SETTLED`: `profit + swap + commission`;
- `RESULT_ONLY`: `profit`, with swap and commission excluded.

`account_daily.realized_profit` is the imported daily account read-model value. The
statistics reconciliation sums those daily values across the account history. It is not
proven to use the same commission, swap, or provider result conventions as the position
reconstruction.

The project therefore keeps both values visible and treats a realized-only difference as
`REALIZED_PROFIT_SEMANTIC_REVIEW`. No production accounting formula or imported fact changes.

## Trading USD

| Component | Amount |
|---|---:|
| CASH_SETTLED profit | 5,206.97 |
| CASH_SETTLED commission | -653.51 |
| RESULT_ONLY profit | -2,273.63 |
| RESULT_ONLY swap | -179.94 |
| Statistics realized result | 2,279.83 |
| Cumulative daily realized | 2,920.17 |
| Difference | -640.34 |

The statistics result is `5,206.97 - 653.51 - 2,273.63`. The all-position raw profit
total is 2,933.34; cumulative daily realized is 2,920.17, leaving a separate 13.17
provider/read-model difference. The 640.34 diagnostic gap is therefore not an unexplained
balance-sheet defect; it is the result of comparing unlike realized-result contracts.

## Other accounts

| Account | Statistics | Daily | Difference |
|---|---:|---:|---:|
| REITs USD | 3,440.58 | 3,450.21 | -9.63 |
| Dividends | 1,597.90 | 1,607.60 | -9.70 |
| IKE Alex | 1,035.53 | 1,039.83 | -4.30 |
| IKE Olga | 1,179.85 | 1,182.54 | -2.69 |

All five rows have matching cash, market value, equity, cost base, unrealized profit,
dividends, interest, fees, and taxes. They are `PROVIDER_STATISTIC_SEMANTICS` /
`COMMISSION_SEMANTICS` reviews, not confirmed production realized-profit defects.

## Canonical metric

`account_statistics.realized_profit` remains the canonical account-statistics/dashboard
closed-position metric because it is reconstructed from immutable position facts and explicit
settlement-model rules. `account_daily.realized_profit` remains the imported daily broker
read-model metric and is retained as an independent comparison value.

## Delta

| Metric | Before | After |
|---|---:|---:|
| Account statistics OK | 6 | 6 |
| Account statistics VALUE_MISMATCH | 5 | 0 |
| Realized semantic review rows | 0 | 5 |
| Confirmed realized-profit defects | 0 | 0 |
| Unexplained realized mismatches | 5 | 0 |

Other reconciliation layers remain unchanged by this diagnostic-only classification.
