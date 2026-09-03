# Local-profile materialized-view performance baseline

Date: 2026-09-01  
Purpose: baseline for comparing materialized-view refresh performance during optimization work.

## Scope

The test connected directly through the PostgreSQL JDBC driver to the Investory dev-container local
profile database. Application materialized views were refreshed first, followed by reconciliation
materialized views, preserving the order in the deployed refresh functions.

No application source or migration was applied to the database during this investigation. Database
operations performed were:

- direct, non-concurrent `REFRESH MATERIALIZED VIEW` for each deployed materialized view;
- `EXPLAIN (ANALYZE, BUFFERS, SETTINGS, SUMMARY, TIMING)` of each materialized-view definition;
- connection-local `SET jit=off` experiments for the two slowest views;
- `ANALYZE` of the 60 regular and materialized relations in the `investory` schema;
- a second ordered refresh and EXPLAIN pass after statistics were repaired.

## Environment

| Property | Value |
| --- | --- |
| Database | `investory` |
| Database user | `investory` |
| Host mapping | `localhost:51098` to container port `5432` |
| PostgreSQL | 17.10, Debian package |
| JDBC driver | PostgreSQL JDBC 42.7.11 |
| Container | `devcontainer-db-1`, `postgres:17-bookworm` |
| JIT | enabled, default source |
| `jit_above_cost` | 100,000 |
| `jit_inline_above_cost` | 500,000 |
| `jit_optimize_above_cost` | 500,000 |
| `work_mem` | 4 MB |
| `shared_buffers` | 160 MB approximately |
| `track_io_timing` | off |
| `pg_stat_statements` | not installed |

The database's latest Flyway migration was from an earlier pre-squash chain. It did not match the
current checkout's squashed migration sources; see [Source/database drift](#sourcedatabase-drift).

## Dataset

The local database did not contain representative transactional volume:

| Relation | Exact rows before analysis |
| --- | ---: |
| `portfolios` | 1 |
| `accounts` | 11 |
| `assets` | 307 |
| `asset_price_history` | 292 |
| `exchange_rates` | 50 |
| `positions` | 0 |
| `account_daily` | 0 |
| `cash_operations` | 0 |
| `import_history` | 0 |

This baseline reliably identifies startup, planning, statistics, and JIT problems. It does not prove
join, sorting, FX-resolution, or index behavior at production-scale cardinalities.

## Executive result

The dominant bottleneck was stale PostgreSQL statistics causing severely inflated plan costs and
expensive JIT compilation.

| Materialized view | Initial refresh | Refresh with `jit=off` | Refresh after `ANALYZE` |
| --- | ---: | ---: | ---: |
| `recon_v_reconstructed_position_daily_mv` | 12,764.679 ms | 121.353 ms | 200.726 ms |
| `recon_v_trade_settlement` | 40,370.138 ms | 127.229 ms | 149.037 ms |

The reconciliation refresh sequence fell from 53.76 seconds to 0.73 seconds after statistics repair,
a reduction of approximately 98.6%.

Before `ANALYZE`, key source tables had `last_analyze = null` and `last_autoanalyze = null`. Several
had `reltuples = -1`, including `accounts`, `positions`, `account_daily`, and `cash_operations`.

### JIT evidence

| Measurement | Reconstructed positions | Trade settlement |
| --- | ---: | ---: |
| Pre-analysis root plan cost | 14,039,094.15 | 49,014,095.63 |
| Pre-analysis EXPLAIN execution | 13,996.709 ms | 32,440.723 ms |
| First root-node activity | about 13,941 ms | about 32,283 ms |
| EXPLAIN execution with `jit=off` | 2.875 ms | 5.429 ms |
| Root cost after `ANALYZE` | 232.89 | 650.22 |
| EXPLAIN execution after `ANALYZE` | 0.999 ms | 9.553 ms |

Almost all pre-analysis execution time occurred before normal plan-node work. Once statistics reduced
estimated cost below the JIT threshold, refresh time returned to sub-second levels.

## Application materialized views

Times are single wall-clock observations. `Plan / execute` is the final EXPLAIN result after statistics
repair.

| Order | Materialized view | Initial refresh | Post-analysis refresh | Plan / execute | Result rows | Baseline observation |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 1 | `app_v_account_monthly` | 855.467 ms | 297.301 ms | 18.366 / 1.602 ms | 0 | Window, grouping, and sorting pipeline. No current account-daily rows exercised it. |
| 2 | `app_v_portfolio_monthly` | 111.878 ms | 126.493 ms | 17.128 / 0.608 ms | 0 | Multiple window and incremental-sort stages. Planning dominates at this scale. |
| 3 | `account_statistics` | 848.895 ms | 1,001.613 ms | 83.589 / 5.549 ms | 11 | Most complex application plan; many joins and aggregates. Planning is the measured cost. |
| 4 | `app_v_portfolio_currency_breakdown` | 144.907 ms | 792.776 ms | 49.958 / 2.053 ms | 0 | Append with position, cash, and FX branches. Refresh-wall-time variation is not explained by query execution. |
| 5 | `app_v_portfolio_asset_allocation` | 38.456 ms | 177.239 ms | 20.206 / 0.370 ms | 0 | Cheap aggregation over `app_v_open_position_values`. |
| 6 | `app_v_symbol_performance` | 75.432 ms | 253.288 ms | 33.062 / 0.991 ms | 0 | Hash joins across positions, assets, cash, and open-position values. Needs representative data. |
| 7 | `app_v_portfolio_kpi_summary_mv` | 32.705 ms | 107.704 ms | 6.367 / 0.451 ms | 1 | Cheap summary join over portfolio and account statistics. |

Initial application total: 2.108 seconds.  
Post-analysis application total: 2.756 seconds.

The sub-second differences are dominated by refresh relation replacement/index maintenance,
transaction commit, container storage, and measurement noise. EXPLAIN execution stayed small.

## Reconciliation materialized views

| Order | Materialized view | Initial refresh | Post-analysis refresh | Plan / execute | Result rows | Baseline observation |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 1 | `recon_v_reconstructed_position_daily_mv` | 12,764.679 ms | 200.726 ms | 15.096 / 0.999 ms | 0 | Primary stale-statistics/JIT victim. Depends on positions, account dates, normalized prices, and portfolio FX. |
| 2 | `recon_v_reconstructed_account_market_daily_mv` | 21.012 ms | 43.821 ms | 0.590 / 0.083 ms | 0 | Simple aggregate over reconstructed position rows. No observed bottleneck. |
| 3 | `recon_v_reconstructed_cash_daily_mv` | 145.887 ms | 72.209 ms | 23.297 / 0.666 ms | 0 | Also susceptible to inflated cost/JIT before statistics repair; uses cash, account dates, and FX. |
| 4 | `recon_v_account_daily_reconciliation_mv` | 77.867 ms | 80.939 ms | 47.973 / 1.013 ms | 0 | Planning-heavy join of account daily, reconstructed market/cash, and realized-result diagnostics. |
| 5 | `recon_v_account_monthly_profit` | 70.671 ms | 51.274 ms | 28.910 / 0.446 ms | 0 | No execution bottleneck on this dataset. |
| 6 | `recon_v_account_statistics_vs_daily` | 61.484 ms | 38.555 ms | 5.143 / 4.333 ms | 11 | Highest meaningful post-repair execution work, but still only about 4 ms. |
| 7 | `recon_v_account_daily_cashflow` | 111.529 ms | 41.294 ms | 32.078 / 1.596 ms | 0 | Merge/window path; planning dominates. |
| 8 | `recon_v_account_daily_cashflow_scope` | 135.646 ms | 48.857 ms | 21.472 / 0.361 ms | 0 | Cheap downstream scope join. |
| 9 | `recon_v_trade_settlement` | 40,370.138 ms | 149.037 ms | 69.916 / 9.553 ms | 0 | Largest stale-statistics/JIT victim. Structurally complex position, cash, price, and FX plan. |

Initial reconciliation total: 53.759 seconds.  
Post-analysis reconciliation total: 0.727 seconds.

## Plan and index observations

- No EXPLAIN plan reported an external sort or temporary-file spill.
- The observed plans were predominantly served from shared-buffer hits.
- Per-operation I/O time could not be measured because `track_io_timing` was off.
- Persistent query history was unavailable because `pg_stat_statements` was not installed.
- Every deployed materialized view had a usable unique index. The current source's concurrent refresh
  contract is therefore structurally supported for the deployed views.
- Existing source indexes cover common position account/asset/time access and price history
  asset/date access. Their usefulness at realistic cardinality was not tested by this dataset.

## Direct dependencies

| Materialized view | Direct Investory dependencies reported by PostgreSQL |
| --- | --- |
| `app_v_account_monthly` | `account_daily`, `accounts`, `portfolios` |
| `app_v_portfolio_monthly` | `app_v_portfolio_performance_daily` |
| `account_statistics` | `account_daily`, `accounts`, `normalized_cash_operations`, `portfolios`, `positions`, `app_v_current_open_position_rows` |
| `app_v_portfolio_currency_breakdown` | `account_daily`, `accounts`, `normalized_cash_operations`, `portfolios`, `positions` |
| `app_v_portfolio_asset_allocation` | `app_v_open_position_values` |
| `app_v_symbol_performance` | `accounts`, `assets`, `normalized_cash_operations`, `portfolios`, `positions`, `app_v_open_position_values` |
| `app_v_portfolio_kpi_summary_mv` | `account_statistics`, `accounts`, `portfolios`, `app_v_portfolio_daily` |
| `recon_v_reconstructed_position_daily_mv` | `account_daily`, `accounts`, `assets`, `positions`, `app_v_normalized_daily_price`, `app_v_portfolio_daily_fx_rate` |
| `recon_v_reconstructed_account_market_daily_mv` | `recon_v_reconstructed_position_daily_mv` |
| `recon_v_reconstructed_cash_daily_mv` | `account_daily`, `accounts`, `normalized_cash_operations`, `app_v_portfolio_daily_fx_rate` |
| `recon_v_account_daily_reconciliation_mv` | `account_daily`, `recon_v_reconstructed_account_market_daily_mv`, `recon_v_reconstructed_cash_daily_mv`, `recon_v_realized_result` |
| `recon_v_account_monthly_profit` | `account_daily`, `app_v_account_monthly`, `normalized_cash_operations` |
| `recon_v_account_statistics_vs_daily` | `account_daily`, `account_statistics`, `accounts` |
| `recon_v_account_daily_cashflow` | `account_daily`, `normalized_cash_operations` |
| `recon_v_account_daily_cashflow_scope` | `normalized_cash_operations`, `recon_v_account_daily_cashflow` |
| `recon_v_trade_settlement` | `account_daily`, `accounts`, `assets`, `cash_operations`, `portfolios`, `positions`, `app_v_canonical_asset_daily_price`, `app_v_portfolio_daily_fx_rate` |

## Source/database drift

The deployed database function `refresh_app_views()` contained seven application materialized
views. The current checkout contains eight and inserts `app_v_portfolio_contribution_summary_mv` after
`account_statistics`.

- Current application refresh order: `V01.001__functions.sql`, starting at line 460.
- Current reconciliation refresh order: `V01.001__functions.sql`, starting at line 592.
- `app_v_portfolio_contribution_summary_mv` definition: `V01.005__portfolio_views.sql`, starting at line 5372.
- The database did not contain `app_v_portfolio_contribution_summary_mv`, so no baseline timing was possible.

The linked `V01.000__schema.sql` contains foundational table definitions. In the current checkout,
materialized-view definitions are primarily in `V01.005__portfolio_views.sql`, and refresh functions
are in `V01.001__functions.sql`.

## Cumulative dependency analysis

The dependency counts below come from a recursive `pg_rewrite`/`pg_depend` traversal of the 16
materialized views deployed in the measured database. A count includes every deployed MV that reaches
the entity directly or through another view/MV. It does not include the entity itself when the entity
is already an MV.

High fan-out does not automatically mean high cost. Small identity/dimension tables such as
`accounts`, `assets`, and `portfolios` reach many MVs but normally join by primary key. Priority is
based on fan-out together with row multiplication, repeated computation, refresh-order position, and
the measured plans.

### Main cumulative paths

```text
account_daily dates + positions
  -> active position/date expansion
  -> app_v_normalized_daily_price
       -> app_v_canonical_asset_daily_price -> asset_price_history
  -> app_v_portfolio_daily_fx_rate -> FX resolver -> exchange_rates
  -> recon_v_reconstructed_position_daily_mv
  -> recon_v_reconstructed_account_market_daily_mv
  -> recon_v_account_daily_reconciliation_mv

cash_operations
  -> normalized_cash_operations
       -> repeated classification and portfolio/account/transaction FX resolution
  -> app statistics, currency, symbol, contribution, and monthly reporting
  -> reconstructed cash and cash-flow reconciliation
  -> recon_v_account_daily_reconciliation_mv

closed positions + matching opened positions + ledger rows + prices + FX
  -> recon_v_trade_settlement
```

### Recursive fan-out

| Entity | Deployed downstream MVs | Cumulative mechanism | Priority interpretation |
| --- | ---: | --- | --- |
| `accounts` | 16 | Ownership/currency join in every path | Very high fan-out but small dimension; not a first performance target. Keep statistics current. |
| `account_daily` | 14 | Supplies reporting facts and the valuation-date spine used to expand historical positions and FX dates | **Highest data-shape priority.** History length can multiply positions, prices, and FX work. |
| `assets` | 14 | Identity and price/position joins | Dimension itself is cheap; asset count amplifies price selection and position-date expansion. |
| `portfolios` | 14 | Ownership and base-currency joins | Small dimension. Base currency causes downstream FX work, not expensive by itself. |
| `cash_operations` | 13 | Reclassified and FX-normalized in many app and reconciliation branches | **High priority.** Ledger growth is rescanned through reusable but non-materialized logic. |
| `positions` | 10 | Current valuation, historical position/date expansion, statistics, allocation, symbol and settlement diagnostics | **Highest data-shape priority.** Position count and holding duration multiply work. |
| `normalized_cash_operations` | 10 | Shared classification and conversion view reused by many independently refreshed MVs | **High shared-computation priority.** Each consumer can re-execute the same classification/FX work. |
| `asset_price_history` | 9 | Canonical daily selection, current prices and historical reconstruction | **High scale priority.** Price history length increases candidate ranking work. |
| `app_v_canonical_asset_daily_price` | 9 | `DISTINCT ON` canonicalization and downstream current/historical price selection | **High shared-computation priority.** Repeated canonical sorting/ranking can accumulate. |
| `app_v_portfolio_daily_fx_rate` | 5 | Portfolio/date/currency cross-product with lateral FX resolution | **High cost-per-reference priority.** Fan-out understates impact because it is joined several times inside individual MVs. |
| `app_v_current_open_position_rows` | 5 | Current positions with two lateral FX resolutions | Medium/high priority when open-position volume grows. |
| `app_v_normalized_daily_price` | 3 | Asset/date expansion, historical candidate join, window ranking and correlated latest-date check | **Very high cost-per-reference priority.** It sits inside the reconstructed-position critical path. |
| `account_statistics` | 2 plus itself | Feeds KPI and statistics-vs-daily reconciliation | Medium priority. Largest measured app planning time and one of the largest app refresh times. |
| `recon_v_reconstructed_position_daily_mv` | 2 plus itself | Feeds reconstructed account market, then account reconciliation | **Very high stage priority.** Early expensive fact; improvement carries into later recon stages. |
| `recon_v_reconstructed_cash_daily_mv` | 1 plus itself | Feeds account reconciliation | Medium priority after normalized-cash and FX work is understood. |
| `recon_v_account_daily_cashflow` | 1 plus itself | Feeds cash-flow scope | Medium/low priority; measured execution was small. |
| `recon_v_trade_settlement` | terminal | Repeats position, price and FX lookups inside one large report | **High local-query priority**, but optimization benefits only this terminal MV unless shared inputs are improved. |

The current checkout adds `app_v_portfolio_contribution_summary_mv`, which was absent from the measured
database. It adds another scan of `normalized_cash_operations`, `accounts`, and `portfolios`, raising
the cumulative importance of normalized cash and FX. It is terminal in the application refresh graph,
so optimizing it alone has little downstream benefit.

### Priority investigation order

#### Priority 0: statistics and maintenance-session JIT policy

This is already a proven bottleneck, not a hypothesis. Stale source statistics caused 53.76 seconds
of reconciliation refresh work on an effectively empty transactional dataset. Establish when source
tables are analyzed after imports and test transaction-local `jit=off` for the maintenance path.

Expected cumulative effect: all MVs receive better estimates; the two observed JIT cliffs disappear.

#### Priority 1: historical position/date reconstruction spine

Investigate together:

- `account_daily` date cardinality and retention;
- active holding duration in `positions`;
- `app_v_normalized_daily_price` candidate count per `(asset_id, valuation_date)`;
- `recon_v_reconstructed_position_daily_mv` rows per position and per account date;
- whether full rebuild, bounded rebuild, partitioning, or safe incremental reconstruction fits the
  reconciliation contract.

This is the strongest multiplicative path. `app_v_normalized_daily_price` first forms distinct
asset/date pairs from positions and `account_daily`, then joins all eligible historical prices,
ranks them, and performs a correlated maximum-date check. `recon_v_reconstructed_position_daily_mv` also
expands each active position across every matching account date. Small inefficiencies here multiply
before three reconciliation stages consume the result.

Expected cumulative effect: reconstructed positions, reconstructed account market, and account-daily
reconciliation improve together. Price and FX lookup demand also falls if the expanded rowset falls.

#### Priority 2: FX resolution and `app_v_portfolio_daily_fx_rate`

Measure:

- distinct `(portfolio_id, valuation_date, source_currency)` combinations;
- rows emitted by the portfolio/date cross-product with `currencies`;
- calls and buffer work inside `resolve_portfolio_fx_rate`, `resolve_fx_rate`, and
  `resolve_transaction_fx_rate`;
- repeated joins to `app_v_portfolio_daily_fx_rate` inside one query;
- exchange-rate candidate counts and index use.

The five-MV catalog fan-out understates this entity. Reconstructed positions use acquisition and
valuation FX; trade settlement joins FX for close cost, profit, commission, opened lots, ledger rows,
previous price, and current price. Normalized cash separately resolves portfolio, account, and
transaction conversion paths.

Expected cumulative effect: reconstructed position/cash facts, trade settlement, current valuation,
portfolio reporting, and normalized cash consumers can all improve.

#### Priority 3: normalize cash once per refresh cycle

`normalized_cash_operations` reaches ten deployed MVs and the current source adds
`app_v_portfolio_contribution_summary_mv`. Investigate whether repeated classification, regular-expression
matching, comment parsing, and FX resolution dominate at realistic ledger volume. Compare the current
view with a correctly indexed/materialized intermediate or a refresh-cycle temporary stage while
preserving normalized-ledger semantics.

Expected cumulative effect: account statistics, portfolio currency breakdown, symbol performance,
contribution summary, reconstructed cash, monthly profit, daily cash-flow reconciliation, cash-flow
scope, and account reconciliation can benefit.

#### Priority 4: canonical and normalized price selection

Measure canonical rows per `(asset_id, price_date)`, candidates per valuation date, rows removed by
ranking, sort memory/spills, and repeated reads of `app_v_canonical_asset_daily_price`. Preserve the
quality/freshness ordering contract when considering indexes or intermediate materialization.

Expected cumulative effect: reconstructed positions plus current-value consumers such as account
statistics, allocation, KPI, symbol performance, and trade settlement can benefit.

#### Priority 5: terminal trade-settlement query

After shared position/price/FX work is measured, inspect query-local repetition in
`recon_v_trade_settlement`:

- repeated scans of `positions` for closed, opened, previous-open, and current-open quantities;
- repeated lateral latest-price lookups for previous and current dates;
- repeated joins to portfolio daily FX;
- date casts in join predicates;
- aggregation grain `(account_id, asset_id, valuation_date)` and supporting indexes.

This was the largest stale-stat/JIT victim. It remains structurally complex, but because it is a
terminal MV, query-local work should be optimized after shared inputs unless representative data
shows it still dominates.

#### Priority 6: `account_statistics` and downstream app summaries

`account_statistics` feeds `app_v_portfolio_kpi_summary_mv` and
`recon_v_account_statistics_vs_daily`. It had the largest post-analysis app refresh
time and 83.6 ms planning time, but only 5.5 ms measured execution on the sparse dataset. Investigate
after representative data exists; current evidence shows plan complexity, not an execution
bottleneck.

#### Lower priority

`recon_v_reconstructed_account_market_daily_mv`, `app_v_portfolio_asset_allocation`, `app_v_portfolio_monthly`,
`app_v_portfolio_kpi_summary_mv`, and the small terminal reconciliation reports had low measured execution and
little downstream fan-out. Optimize them only if representative-volume plans change that result.

## Optimization priorities

1. Ensure source-table statistics are current before materialized-view refreshes after bulk import.
   Determine whether explicit source-table `ANALYZE`, autovacuum tuning, or import-triggered statistics
   maintenance is the correct operational contract.
2. Test a connection- or transaction-local `jit=off` policy for maintenance refresh sessions. Do not
   globally disable JIT without representative-volume evidence.
3. Repeat the test with representative `positions`, `account_daily`, and `cash_operations` history.
4. Focus scale investigation on:
   - `recon_v_trade_settlement`;
   - `recon_v_reconstructed_position_daily_mv`;
   - `account_statistics`;
   - `app_v_portfolio_currency_breakdown`.
5. During representative testing, enable `track_io_timing` and use `pg_stat_statements` if permitted.
6. Inspect repeated FX resolution under scale. Evaluate an index beginning with
   `(base, to_currency, rate_date, method, observed_at)` only after EXPLAIN proves it is needed.

## Comparison procedure for later runs

Record both cold and repeated runs. Do not compare a single cold baseline with only a warm optimized
run.

For each run:

1. Record Git revision, Flyway version, PostgreSQL version, container resources, and database counts.
2. Record `pg_stat_user_tables.last_analyze`, `last_autoanalyze`, `reltuples`, and exact row counts for
   the primary source tables.
3. Record JIT and memory settings.
4. Refresh application MVs in function order, then reconciliation MVs in function order.
5. Capture wall-clock refresh time per MV.
6. Capture `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)` for every definition.
7. Record planning time, execution time, root estimated cost/rows, actual rows, buffer reads/hits,
   temporary I/O, JIT time, and WAL volume.
8. Compare correctness row counts and reconciliation statuses as well as time.

### Future comparison table

| Materialized view | Baseline refresh | Candidate refresh | Delta | Baseline plan/execute | Candidate plan/execute | Rows equal | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| `app_v_account_monthly` | 297.301 ms | | | 18.366 / 1.602 ms | | | |
| `app_v_portfolio_monthly` | 126.493 ms | | | 17.128 / 0.608 ms | | | |
| `account_statistics` | 1,001.613 ms | | | 83.589 / 5.549 ms | | | |
| `app_v_portfolio_currency_breakdown` | 792.776 ms | | | 49.958 / 2.053 ms | | | |
| `app_v_portfolio_asset_allocation` | 177.239 ms | | | 20.206 / 0.370 ms | | | |
| `app_v_symbol_performance` | 253.288 ms | | | 33.062 / 0.991 ms | | | |
| `app_v_portfolio_kpi_summary_mv` | 107.704 ms | | | 6.367 / 0.451 ms | | | |
| `recon_v_reconstructed_position_daily_mv` | 200.726 ms | | | 15.096 / 0.999 ms | | | |
| `recon_v_reconstructed_account_market_daily_mv` | 43.821 ms | | | 0.590 / 0.083 ms | | | |
| `recon_v_reconstructed_cash_daily_mv` | 72.209 ms | | | 23.297 / 0.666 ms | | | |
| `recon_v_account_daily_reconciliation_mv` | 80.939 ms | | | 47.973 / 1.013 ms | | | |
| `recon_v_account_monthly_profit` | 51.274 ms | | | 28.910 / 0.446 ms | | | |
| `recon_v_account_statistics_vs_daily` | 38.555 ms | | | 5.143 / 4.333 ms | | | |
| `recon_v_account_daily_cashflow` | 41.294 ms | | | 32.078 / 1.596 ms | | | |
| `recon_v_account_daily_cashflow_scope` | 48.857 ms | | | 21.472 / 0.361 ms | | | |
| `recon_v_trade_settlement` | 149.037 ms | | | 69.916 / 9.553 ms | | | |

The comparison baseline uses post-`ANALYZE` values because those represent a valid optimizer-statistics
state. The initial stale-statistics timings remain documented as a diagnosed failure mode.
