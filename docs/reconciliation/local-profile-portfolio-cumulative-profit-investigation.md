# Portfolio cumulative P/L investigation

Generated: 2026-08-12

Scope: local profile database. No imported facts or production rows were changed.

## Classification

The product contract is now **NON-CASH-ONLY INVESTMENT PERFORMANCE**. Cash-only
accounts remain available for balances, cash, and net-worth surfaces, but are
excluded from investment P/L, cumulative performance, monthly performance, and
benchmark performance.

The daily chart and the new canonical portfolio performance view exclude
accounts marked `accounts.cash_only = true`. Whole-portfolio balance/equity
columns remain available from `v_portfolio_daily`.

## A. Exact data paths

### Monthly P/L

```text
account_daily
  -> account_monthly_mv
  -> AccountMonthlyPerformanceRepository
  -> PortfolioService.calculateMonthlyPerformance()
  -> dashboard.overview.monthlyPerformance
  -> monthly-performance-chart
```

`account_monthly_mv` converts account-daily monetary values to portfolio base
currency using the snapshot-date FX rate, then sums `daily_profit_amount` into
`total_profit`. The monthly chart uses these totals or account attribution rows
for a selected-account subset.

### Daily cumulative Portfolio P/L

```text
account_daily
  -> BenchmarkService.accountValueYears()
  -> dailyAccountValueSeries()
  -> AccountValueYear.totalProfitValues
  -> dashboard.html updateAccountValueChart()
  -> account-value-chart / account-value-table
```

Before the fix, `BenchmarkService.calculate()` removed cash-only accounts:

```java
availableAccounts.removeAll(cashOnlyAccounts);
accountValueAccounts.removeAll(cashOnlyAccounts);
```

After the fix, `performanceAccounts` is the set of all non-null account IDs in
`account_daily` minus `cash_only` accounts. This scope is used by the daily
performance chart. Benchmark/monthly account eligibility remains separately
defined and also excludes cash-only accounts.

For each remaining account, `dailyAccountValueSeries()` accumulates
`AccountDaily.dailyProfitAmount` from the start of the calendar year. The total
series sums those per-account cumulative series. JavaScript only renders the
backend-provided values.

## B. First divergence

The first divergent date is **2026-01-01**.

| Date | All-account daily P/L | Cash-only P/L | Non-cash daily P/L | All cumulative | Non-cash cumulative |
|---|---:|---:|---:|---:|---:|
| 2026-01-01 | -13.44 | -4.14 | -9.30 | -13.44 | -9.30 |
| 2026-01-02 | 893.71 | 3.92 | 889.79 | 880.27 | 880.49 |
| 2026-01-03 | 13.46 | 3.60 | 9.86 | 893.72 | 890.34 |

The chart and canonical performance series now use the non-cash series.

## C. Month-end decomposition

This reproduces the non-cash dashboard calculation from `account_daily`.
`dashboard raw` applies the same two-decimal per-account cumulative rounding
used by Java.

| Date | Prior all-account cumulative | Non-cash canonical cumulative | Dashboard raw | Displayed chart | Gap vs non-cash |
|---|---:|---:|---:|---:|---:|
| 2026-01-31 | 6,677.63 | 6,622.58 | 6,622.58 | 6,623 | -54.63 |
| 2026-02-28 | 7,041.69 | 7,006.86 | 7,006.86 | 7,007 | -34.83 |
| 2026-03-31 | 1,482.71 | 1,454.66 | 1,454.66 | 1,455 | -28.05 |
| 2026-04-30 | 12,577.92 | 12,555.04 | 12,555.05 | 12,555 | -22.87 |
| 2026-05-31 | 16,399.32 | 16,372.56 | 16,372.56 | 16,373 | -26.76 |
| 2026-06-30 | 13,872.85 | 13,851.02 | 13,851.02 | 13,851 | -21.83 |
| 2026-07-31 | 14,013.58 | 13,997.05 | 13,997.04 | 13,997 | -16.54 |
| 2026-08-12 | 20,575.76* | 20,575.76 | 20,575.76 | 20,576 | 0.00* |

The first seven values reproduce the established non-cash daily chart values
after display rounding. The previous monthly mismatch was the cash-only
contribution.

The supplied August canonical value (`20,575.76`) does not match the current
local `account_daily` state used by the dashboard calculation. Current August
account-daily P/L is `$6,777.15`; the supplied snapshot has `$6,562.19`. The
current local refresh reports the same `$20,575.76` non-cash canonical and daily
cumulative value. The old August value is therefore recorded as a pre-refresh
snapshot; its exact producer cannot be proven from current rows.

## D. Semantics and FX

`v_portfolio_daily`:

1. joins all `account_daily` rows to their portfolio;
2. converts equity and daily profit to portfolio base currency with
   `resolve_fx_rate(snapshot_date, valuation_currency, base_currency)`;
3. computes `total_profit` from aggregate equity movement minus external flows.

The daily chart now starts from `AccountDaily.dailyProfitAmount` and explicitly
converts it when the row valuation currency differs from the portfolio base
currency, using `CurrencyRateService.convertToBaseCurrency(..., row.date)`.
This aligns its currency contract with `v_portfolio_daily`.

No equivalent inactive/import-excluded asset filter was found in this path. The
concrete population filter is `cash_only`, not asset activity.

## E. Monthly reconciliation

Monthly totals remain correct under the established contract and match the
supplied values:

```text
2026-01  6,677.63
2026-02    364.06
2026-03 -5,558.98
2026-04 11,095.21
2026-05  3,821.40
2026-06 -2,526.47
2026-07    140.73
2026-08  6,562.19  (prior supplied snapshot)
```

No change to `total_profit` was made.

## F. Recommendation

The implemented performance contract is:

```text
Portfolio P/L scope = accounts with account_daily rows where cash_only = false
currency = portfolio base currency
cumulative P/L(t) = cumulative P/L(t-1) + canonical daily P/L(t)
```

Whole-portfolio balance/equity/cash surfaces remain separate and may include
cash-only accounts.

## G. Regression invariant

For a declared account scope and common base currency:

```text
chart_daily_profit(date, scope)
  = sum(account_daily.daily_profit_amount converted to base currency
        for accounts in scope)

chart_cumulative_profit(t)
  = chart_cumulative_profit(t-1) + chart_daily_profit(t)
```

The test now includes a cash-only account with non-zero historical profit and a
non-USD account to verify both scope and FX conversion.

## H. August stale-data investigation

Evidence collected locally:

| Object | Evidence |
|---|---|
| `account_daily` | latest `updated_at`: `2026-08-12 22:05:12.830451` |
| `account_monthly_mv` | August total: `6,777.15`; `updated_at`: `2026-08-12 22:05:08.028206` |
| `v_portfolio_daily` | live view; `updated_at` is `NOW()` and is not a persisted refresh marker |
| application dashboard path | reads `AccountDaily` and `AccountMonthlyPerformance`; no dashboard snapshot cache found |
| refresh path | `PortfolioProjectionService.recalculateAll()` saves `account_daily`, then calls `refresh_app_views()` |

The current refresh order is deterministic for projection data. The old
`$6,562.19` value cannot be reproduced after the current refresh, and no
independent stale object remains. Its exact producer is unknown; it was not
corrected by changing financial facts. No additional stale-data patch is
justified by the available evidence.

## I. Scope correction implemented

The product decision is now:

```text
investment performance accounts = accounts.cash_only = false
portfolio base currency = explicit snapshot-date FX conversion
```

Implemented paths:

- `BenchmarkService`: `performanceAccounts` excludes cash-only accounts for the
  daily cumulative series; benchmark eligibility remains a separate scope.
- `v_portfolio_performance_daily`: new SQL view filters `NOT a.cash_only` and
  applies `resolve_fx_rate` to `account_daily.daily_profit_amount`.
- `portfolio_monthly_mv`: now reads `v_portfolio_performance_daily`, so monthly
  portfolio P/L, flows, and return use the same non-cash scope.
- `v_portfolio_daily`: whole-portfolio balance/equity/cash fields remain intact
  for valuation and net-worth consumers.
- `PortfolioService`: existing visible-account filtering continues to exclude
  cash-only accounts for the dashboard monthly attribution surface.

Local derived-view rebuild after the change produced these non-cash monthly
portfolio P/L values:

```text
2026-01  6,622.58
2026-02    384.28
2026-03 -5,552.20
2026-04 11,100.38
2026-05  3,817.52
2026-06 -2,521.54
2026-07    146.03
2026-08  6,777.15
```

The values reflect the current refreshed local `account_daily` state. Earlier
report values differed in some months because the local projection data was
subsequently refreshed; no source facts were edited. The daily non-cash view and
monthly MV now share the same account filter and FX conversion.

Regression coverage:

- `BenchmarkServiceTest` proves cash-only daily P/L is excluded.
- `BenchmarkServiceTest` proves non-USD daily P/L uses snapshot-date conversion.
- `MaterializedViewRefreshContractIT` checks the performance view filter and
  portfolio monthly dependency.
