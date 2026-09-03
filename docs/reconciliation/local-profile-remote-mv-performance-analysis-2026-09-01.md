# Supplied local-profile MV performance analysis

Date: 2026-09-01  
Database: `jdbc:postgresql://192.168.1.60:5432/inventory?tcpKeepAlive=true`, schema `investory`, PostgreSQL 14.23  
Access: PostgreSQL JDBC 42.7.11 as `postgres`

## Result

The theory is confirmed, with one important extension.

1. Source-table statistics were stale. `accounts`, `portfolios`, `currencies`, `fx_configuration`,
   `positions`, `account_daily`, `cash_operations`, and several import tables had no `last_analyze`.
2. An explicit source-table `ANALYZE` removed the catastrophic refresh behavior for the first two
   measured application MVs: `app_v_account_monthly` fell from 70,927 ms to 3,848 ms and
   `app_v_portfolio_monthly` from 31,832 ms to 3,230 ms (the post pass used `jit=off`).
3. `account_statistics` remains a separate structural bottleneck. Its post-analysis plan cost is
   3,896,237 and its refresh ran for 173,729 ms with `jit=off` before cancellation. This is not
   explained by stale statistics or JIT compilation alone.

The required sequencing is therefore correct:

`bulk load -> ANALYZE source tables -> app refresh order -> reconciliation refresh order`

## Refresh order and observations

Order is taken from the database functions `refresh_app_views()` and
`refresh_reconciliation_reporting_views()`.

| Order | MV | Root plan cost after source ANALYZE | Refresh evidence | Main finding |
|---:|---|---:|---|---|
| 1 | `app_v_account_monthly` | 44,415 | 70,927 ms stale; 3,848 ms post-analysis/JIT-off | Window/FX pipeline; statistics was the dominant first-pass issue. |
| 2 | `app_v_portfolio_monthly` | 43,478 | 31,832 ms stale; 3,230 ms post-analysis/JIT-off | Aggregate/window/FX pipeline; same statistics/JIT signature. |
| 3 | `account_statistics` | 3,896,237 | 30,016 ms first attempt timed out; 163,613 ms post-analysis/JIT-on cancelled; 173,729 ms JIT-off cancelled | Highest priority structural hotspot; repeated complex joins and aggregates. |
| 4 | `app_v_portfolio_contribution_summary_mv` | 2,687,329 | 65,027 ms stale attempt cancelled; later attempt cancelled while blocked by the long app pass | Repeated cash-operation classification/FX work. |
| 5 | `app_v_portfolio_currency_breakdown` | 1,252,118 | Post-analysis/JIT-off attempt exceeded the bounded run and was cancelled | Three large branches: position, cash, and FX. |
| 6 | `app_v_portfolio_asset_allocation` | 45,225 | Not separately completed after the interrupted sequence | Aggregation over `app_v_open_position_values`; inspect upstream price/FX view. |
| 7 | `app_v_symbol_performance` | 3,093,656 | Post-analysis/JIT-off attempt was cancelled at 17.3 s while running | Repeated position/current-value/cash joins; high estimated cost. |
| 8 | `app_v_portfolio_kpi_summary_mv` | 43,188 | Not separately completed after the interrupted sequence | Downstream summary; depends on `account_statistics`. |
| 9 | `recon_v_reconstructed_position_daily_mv` | 1,196,388 | Not refreshed in the clean post pass | Critical reconstruction spine; expands positions across account dates and resolves prices/FX. |
| 10 | `recon_v_reconstructed_account_market_daily_mv` | 2,776 | Not refreshed in the clean post pass | Simple aggregate over reconstructed positions; not a primary hotspot. |
| 11 | `recon_v_reconstructed_cash_daily_mv` | 2,271,997 | Not refreshed in the clean post pass | Repeated normalized-cash and transaction/portfolio FX work. |
| 12 | `recon_v_account_daily_reconciliation_mv` | 3,496,404 | Not refreshed in the clean post pass | Joins account-day, reconstructed facts, and realized-result diagnostics. |
| 13 | `recon_v_account_monthly_profit` | 2,245,439 | Not refreshed in the clean post pass | Repeats normalized cash and monthly/account-day comparison logic. |
| 14 | `recon_v_account_statistics_vs_daily` | 1,311 | Not refreshed in the clean post pass | Small downstream comparison; low priority. |
| 15 | `recon_v_account_daily_cashflow` | 2,324,487 | Not refreshed in the clean post pass | Repeated cash normalization and account-day merge/window work. |
| 16 | `recon_v_account_daily_cashflow_scope` | 2,253,403 | Not refreshed in the clean post pass | Downstream scope join; cost is inherited from its input view. |
| 17 | `recon_v_trade_settlement` | 3,382,207 | Not refreshed in the clean post pass | Terminal but complex position-close, cash, price, and FX reconstruction. |

“Not refreshed” means the ordered run was stopped after the proven runaway hotspots; it is not a
claim that the MV is broken or that its refresh time is zero.

## Database evidence

Before analysis, representative source cardinalities were: `positions` 9,305,
`account_daily` 5,869, `cash_operations` 20,493, `asset_price_history` 130,161,
`import_source_rows` 46,575, and `exchange_rates` 316. Source analysis itself took about 3.0 s
total, including 1.8 s for price history, 0.5 s for cash operations, 0.5 s for positions, and
1.9 s for import rows.

The cost-only plans repeatedly reference `exchange_rates` through FX resolver branches. The largest
plans also repeatedly expand `normalized_cash_operations`, `app_v_normalized_daily_price`,
`app_v_portfolio_daily_fx_rate`, and current/open-position logic. This indicates repeated inlining of
expensive reusable views rather than one isolated missing index.

## Further investigation and optimization order

1. Make source-table `ANALYZE` part of the post-import/pre-refresh transaction boundary. Include
   dimension tables with `reltuples = -1`, not only the visibly large tables.
2. Decompose `account_statistics` with `EXPLAIN (ANALYZE, BUFFERS, SETTINGS)` under a controlled
   session after reducing the definition to each CTE/branch. Its 3.9M plan cost and >173 s runtime
   make it the first structural target.
3. Inspect repeated FX resolver execution. Consider a materialized or staged portfolio/date/currency
   FX input keyed by `(portfolio_id, snapshot_date, source_currency)`, provided freshness and missing-
   rate semantics remain explicit.
4. Inspect `normalized_cash_operations` reuse. Several MVs independently rescan and reclassify the
   same 20k cash rows. A refresh-stage intermediate relation could remove repeated classification and
   transaction-FX work.
5. For `recon_v_reconstructed_position_daily_mv`, measure row multiplication from position holding duration
   × account-date spine × historical price candidate rows. Validate indexes on `(account_id, date)`,
   `(asset_id, date)`, and the exact lateral lookup predicates with realistic data.
6. Re-run every MV individually with `EXPLAIN (ANALYZE, BUFFERS, SETTINGS, WAL, SUMMARY)` after the
   structural changes. Enable `track_io_timing` and install/use `pg_stat_statements` only if allowed
   by the database operator; neither was available in this run.

## Caveats

The refreshes use the deployed `REFRESH MATERIALIZED VIEW CONCURRENTLY` behavior. Several long
refreshes were cancelled deliberately to prevent orphaned server sessions and lock interference.
No source code, migration, index, or MV definition was changed. The evidence proves the statistics
placement and identifies structural hotspots; it does not yet justify a specific index or schema
rewrite.
