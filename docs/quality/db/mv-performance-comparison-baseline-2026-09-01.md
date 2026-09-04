# Database materialized-view and regular-view performance baseline

Date: 2026-09-01  
Purpose: compare the empty transactional-data baseline with the populated local-profile database and
record the next optimization targets.

Object names in the original measurements used the pre-refactor deployed names; this document maps
them to the current migration names: `app_v_account_monthly`, `app_v_portfolio_monthly`, `app_v_account_statistics`,
`app_v_portfolio_currency_breakdown`, `app_v_portfolio_asset_allocation`, `app_v_symbol_performance`,
`app_v_portfolio_kpi_summary_mv`, `recon_v_account_monthly_profit`, `recon_v_account_statistics_vs_daily`,
`recon_v_account_daily_cashflow`, `recon_v_account_daily_cashflow_scope`, and
`recon_v_trade_settlement`.

## Executive summary

### Current status — 2026-09-02

The latest ordered run against the populated local-profile database completed the application-MV
refresh sequence followed by the reconciliation-MV sequence. It also measured 48 prefixed regular
views with row counts and bounded `EXPLAIN ANALYZE` execution. The strongest current evidence is:

- Stored MV reads are cheap: approximately 2–40 ms for the 20 measured MVs.
- Refresh is the dominant cost. `app_v_account_statistics` took 243.7 s for 11 rows,
  `app_v_portfolio_currency_breakdown` 126.4 s for 7 rows, `app_v_normalized_cash_operations`
  113.2 s for 19,770 rows, and `app_v_symbol_performance` 98.2 s for 185 rows.
- `app_v_normalized_daily_price`, `recon_v_validation_issue`, and `recon_v_realized_result`
  exceeded the 45-second diagnostic timeout during both read and plan execution.
- The next optimization work should target repeated price/FX/cash computation and row-grain
  multiplication, not MV-result indexes or generic timeout increases.

This section is the active tracking baseline. Earlier sections remain historical comparison evidence.

The supplied populated database is materially different from the empty baseline:

- Empty baseline: PostgreSQL 17.10, 1 portfolio, 11 accounts, 0 positions, 0 account-day rows, and
  0 cash operations.
- Populated profile: PostgreSQL 14.23, 1 portfolio, 11 accounts, 9,305 positions, 5,869 account-day
  rows, 20,493 cash operations, 130,161 price-history rows, and 316 FX rows.

The previous theory is confirmed for statistics/JIT cliffs. On the populated profile, source-table
`ANALYZE` changed measured refreshes as follows:

| MV | Before source `ANALYZE` | After source `ANALYZE`, `jit=off` | Change |
|---|---:|---:|---:|
| `account_monthly_mv` | 70,927 ms | 3,848 ms | -94.6% |
| `portfolio_monthly_mv` | 31,832 ms | 3,230 ms | -89.9% |

But `account_statistics` remained above 173 seconds with JIT disabled. This proves a second,
structural bottleneck that statistics maintenance cannot solve by itself.

## Important comparability limits

The empty baseline used direct non-concurrent `REFRESH MATERIALIZED VIEW`; the populated test used
the deployed `REFRESH MATERIALIZED VIEW CONCURRENTLY` functions. Storage, PostgreSQL version,
cache state, row counts, source statistics, and MV contents also differ. Use the numbers below as
diagnostic baselines, not as a strict benchmark ratio.

Empty baseline source: [local-profile-materialized-view-performance-baseline-2026-09-01.md](../../reconciliation/local-profile-materialized-view-performance-baseline-2026-09-01.md)  
Populated baseline source: [local-profile-remote-mv-performance-analysis-2026-09-01.md](../../reconciliation/local-profile-remote-mv-performance-analysis-2026-09-01.md)

## Metadata

| Property | Empty baseline | Populated profile |
|---|---|---|
| Database endpoint | `localhost:51098` container mapping | `192.168.1.60:5432/inventory` |
| Schema | `investory` | `investory` |
| User | `investory` | `postgres` |
| PostgreSQL | 17.10 | 14.23 |
| JDBC driver | 42.7.11 | 42.7.11 |
| Refresh mode | direct, non-concurrent | deployed concurrent mode |
| JIT | enabled, then local off experiments | enabled, then source-analysis/JIT-off experiments |
| `work_mem` | 4 MB | not captured |
| `shared_buffers` | about 160 MB | not captured |
| `track_io_timing` | off | not captured; treated as unavailable |
| `pg_stat_statements` | not installed | not captured; treated as unavailable |

## Populated profile: refresh observations, biggest first

Times are ordered by observed execution time. A `>` value is a lower bound because the operation was
cancelled. `N/M` means no reliable per-MV refresh time was obtained after long-running hotspots
interrupted the ordered pass.

| Rank | MV | Observed refresh time | State | Plan cost after source `ANALYZE` | Concern / next check |
|---:|---|---:|---|---:|---|
| 1 | `account_statistics` | >173,729 ms | cancelled, JIT off | 3,896,237 | Structural repeated joins/aggregates. Decompose definition by CTE and measure each branch. |
| 2 | `account_monthly_mv` | 70,927 ms stale; 3,848 ms analyzed/JIT off | completed | 44,415 | Statistics/JIT cliff confirmed; enforce source `ANALYZE` after bulk load. |
| 3 | `portfolio_contribution_summary` | >65,027 ms | cancelled during stale-stat attempt | 2,687,329 | Repeated cash classification and FX resolution. Investigate reusable normalized-cash stage. |
| 4 | `portfolio_currency_breakdown` | >36,494 ms | cancelled, JIT off | 1,252,118 | Three position/cash/FX branches; measure branch costs separately. |
| 5 | `portfolio_monthly_mv` | 31,832 ms stale; 3,230 ms analyzed/JIT off | completed | 43,478 | Same statistics/JIT pattern; inspect repeated FX/window work after maintenance fix. |
| 6 | `symbol_performance` | >17,300 ms | cancelled, JIT off | 3,093,656 | Repeated current-value, position, and cash joins; validate upstream reuse. |
| 7 | `recon_v_account_daily_reconciliation_mv` | N/M | ordered pass interrupted | 3,496,404 | Joins account-day, reconstructed facts, and realized diagnostics. |
| 8 | `recon_v_trade_settlement` | N/M | ordered pass interrupted | 3,382,207 | Complex terminal position-close, price, cash, and FX logic. |
| 9 | `recon_v_reconstructed_cash_daily_mv` | N/M | ordered pass interrupted | 2,271,997 | Repeated normalized-cash and FX work. |
| 10 | `recon_v_account_daily_cashflow` | N/M | ordered pass interrupted | 2,324,487 | Repeated cash normalization and account-day merge/window work. |
| 11 | `recon_v_account_daily_cashflow_scope` | N/M | ordered pass interrupted | 2,253,403 | Downstream cost likely inherited from cash-flow input. |
| 12 | `recon_v_account_monthly_profit` | N/M | ordered pass interrupted | 2,245,439 | Repeats normalized cash and monthly/account-day comparison. |
| 13 | `recon_v_reconstructed_position_daily_mv` | N/M | ordered pass interrupted | 1,196,388 | Critical position/date/price/FX expansion spine. |
| 14 | `portfolio_asset_allocation` | N/M | ordered pass interrupted | 45,225 | Likely cheap relative to upstream `app_v_open_position_values`; verify with a clean run. |
| 15 | `portfolio_kpi_summary` | N/M | ordered pass interrupted | 43,188 | Downstream of `account_statistics`; do not optimize first. |
| 16 | `recon_v_account_statistics_vs_daily` | N/M | ordered pass interrupted | 1,311 | Small comparison; low priority unless cardinality changes. |
| 17 | `recon_v_reconstructed_account_market_daily_mv` | N/M | ordered pass interrupted | 2,776 | Simple aggregate over reconstructed positions; low independent priority. |

The populated plan-cost ranking is not a runtime ranking. It identifies expensive optimizer work and
possible row multiplication, but actual refresh time must be measured with a clean, complete pass.

## Empty baseline: initial refresh times, biggest first

This is the empty/near-empty database's initial refresh observation, sorted descending.

| Rank | MV | Initial refresh | Post-`ANALYZE` refresh | Main interpretation |
|---:|---|---:|---:|---|
| 1 | `recon_v_trade_settlement` | 40,370.138 ms | 149.037 ms | Stale-statistics/JIT cliff; complex plan but no representative rows. |
| 2 | `recon_v_reconstructed_position_daily_mv` | 12,764.679 ms | 200.726 ms | Stale-statistics/JIT cliff in the reconstruction spine. |
| 3 | `account_monthly_mv` | 855.467 ms | 297.301 ms | Small data; window/FX pipeline. |
| 4 | `account_statistics` | 848.895 ms | 1,001.613 ms | Planning/definition complexity, not meaningful execution volume. |
| 5 | `recon_v_reconstructed_cash_daily_mv` | 145.887 ms | 72.209 ms | Small cash/FX path. |
| 6 | `portfolio_currency_breakdown` | 144.907 ms | 792.776 ms | Refresh variance dominated by storage/index work at this size. |
| 7 | `recon_v_account_daily_cashflow_scope` | 135.646 ms | 48.857 ms | Downstream scope path. |
| 8 | `portfolio_monthly_mv` | 111.878 ms | 126.493 ms | Small window/aggregate path. |
| 9 | `recon_v_account_daily_cashflow` | 111.529 ms | 41.294 ms | Small merge/window path. |
| 10 | `recon_v_account_daily_reconciliation_mv` | 77.867 ms | 80.939 ms | Small planning-heavy join. |
| 11 | `symbol_performance` | 75.432 ms | 253.288 ms | Needs representative positions and cash. |
| 12 | `recon_v_account_monthly_profit` | 70.671 ms | 51.274 ms | No observed execution bottleneck. |
| 13 | `recon_v_account_statistics_vs_daily` | 61.484 ms | 38.555 ms | Small comparison. |
| 14 | `portfolio_asset_allocation` | 38.456 ms | 177.239 ms | Cheap aggregation with no open positions. |
| 15 | `portfolio_kpi_summary` | 32.705 ms | 107.704 ms | Cheap downstream summary. |
| 16 | `recon_v_reconstructed_account_market_daily_mv` | 21.012 ms | 43.821 ms | Simple aggregate. |

The empty report measured 16 deployed MVs. `portfolio_contribution_summary` was absent from that
database, so it has no empty-database timing.

## What changed with data volume

The remote profile exposes the real scaling risks that the empty baseline could not show:

- `positions` × account-date history can multiply rows in `recon_v_reconstructed_position_daily_mv`.
- `asset_price_history` candidate selection and canonicalization now operate over 130k rows.
- `cash_operations` and normalized cash logic are rescanned by many MVs.
- FX resolution is repeated through portfolio/date/currency and transaction-level branches.
- Several plans still have million-level estimated costs after source statistics were repaired.

The key distinction is:

`ANALYZE` fixes bad estimates and prevents JIT compilation from dominating, but it does not remove
repeated computation or row multiplication inside the MV definitions.

## Recommended evaluation order

1. Make source-table `ANALYZE` an explicit post-import/pre-refresh operation.
2. Measure `account_statistics` branch by branch; it is the first confirmed structural hotspot.
3. Measure and potentially stage `normalized_cash_operations` once per refresh cycle.
4. Measure reusable FX results keyed by portfolio/date/source currency.
5. Investigate position/date and historical-price row multiplication in
   `recon_v_reconstructed_position_daily_mv`.
6. Re-run all 17 MVs with identical mode, PostgreSQL version, cache state, and captured settings.
7. Use `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, SUMMARY)` plus `track_io_timing` and
   `pg_stat_statements` when available.

Do not add indexes or rewrite the schema from plan cost alone. First capture actual rows, buffer
hits/reads, temporary I/O, JIT timing, and per-branch execution time on the populated profile.

## View performance: populated profile

The schema contains 88 regular views. Cost-only plans were captured for the shared views that feed
multiple MVs or sit on the critical reconstruction/reconciliation paths. The cost column is an
optimizer estimate, not elapsed execution time. All view plan checks used `jit=off`.

| Rank by plan cost | View | Root plan | Plan cost | Runtime evidence | Concern / follow-up |
|---:|---|---|---:|---|---|
| 1 | `recon_v_realized_result` | Merge Join | 3,417,538 | Not completed | Large realized-result join; inspect date/position/ledger row multiplication. |
| 2 | `normalized_cash_operation_flows` | Subquery Scan | 2,290,727 | Not completed | Reuses normalized cash classification and FX; likely duplicates expensive work. |
| 3 | `normalized_cash_operations` | Nested Loop | 2,281,780 | `EXPLAIN ANALYZE` exceeded 32 s and was cancelled, JIT off | First shared-view runtime hotspot. Break down classification, account/portfolio joins, and transaction FX. |
| 4 | `app_v_normalized_daily_price` | Subquery Scan | 754,810 | Not completed | Historical candidate ranking and latest-date checks can multiply by asset/date history. |
| 5 | `app_v_open_position_values` | Subquery Scan | 45,239 | Not completed | Shared by allocation, symbol performance, and statistics; inspect price/FX reuse. |
| 6 | `app_v_current_open_position_rows` | Nested Loop | 43,683 | Not completed | Current positions with lateral FX lookups; validate lookup cardinality and indexes. |
| 7 | `app_v_portfolio_performance_daily` | WindowAgg | 43,267 | Not completed | Window pipeline over portfolio daily facts; inspect sort and date spine size. |
| 8 | `app_v_canonical_asset_daily_price` | Unique | 22,111 | Not completed | Canonicalization likely uses ordered price-history selection; check rows removed by ranking. |
| 9 | `app_v_portfolio_daily_fx_rate` | Nested Loop | 20,972 | Not completed | Portfolio/date/currency cross-product with lateral FX resolution; high cost per reference. |
| 10 | `recon_v_account_daily_cashflow` | Seq Scan | 148 | Not completed | Low estimated cost; downstream scope view should not be an initial target. |

The ranking above is by plan cost because only one view produced a bounded runtime observation.
The direct runtime finding is still significant: `normalized_cash_operations` could not finish a
full `EXPLAIN ANALYZE` in the 32-second diagnostic window even with JIT disabled, while its cost is
2.28M. This supports the MV evidence that normalized cash is a shared structural bottleneck, not
only a stale-statistics problem.

The other 78 views are mostly adapters, quality/reporting views, or low-fan-out views. They remain
part of the inventory, but broad execution of all 88 views would repeat the same expensive source
plans and would not improve prioritization before the shared views above are decomposed.

### View-specific optimization ideas

- `normalized_cash_operations` / `normalized_cash_operation_flows`: materialize or stage one
  refresh-cycle result only if freshness and classification semantics remain identical; otherwise
  reduce repeated FX resolver calls and verify predicates on `cash_operations` and `exchange_rates`.
- `recon_v_realized_result`: isolate closed/open lot matching, ledger matching, and FX
  conversion into separate plans; measure each join's actual rows and buffer activity.
- `app_v_normalized_daily_price` / `app_v_canonical_asset_daily_price`: measure candidate rows per
  `(asset_id, valuation_date)`, ranking waste, and latest-price lateral lookups before changing
  indexes or materialization.
- `app_v_portfolio_daily_fx_rate`: measure distinct portfolio/date/source-currency keys and cache or
  stage one canonical result per key when the domain contract permits it.
- `app_v_current_open_position_rows` / `app_v_open_position_values`: inspect repeated current-price and FX
  lookups; these views are upstream of several application MVs.
- Adapter views such as `app_v_*` and `recon_v_*`: optimize only when a consumer plan proves the
  adapter adds work; do not mistake view count for runtime cost.

## 2026-09-02 ordered execution evidence

### Measurement contract

The active local profile was used: PostgreSQL 14.23, `inventory`, schema `investory`,
`Europe/Warsaw`, `jit=on`, `work_mem=4MB`. The application order came from
`PortfolioProjectionRefreshService`; application MVs were measured first, then reconciliation MVs.
MV refresh used `REFRESH MATERIALIZED VIEW` plus `ANALYZE`. Reads used `SELECT count(*)`.
Plans used `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON) SELECT *` with a 45-second
statement timeout. No schema or optimization changes were made.

Exact per-object diagnostic outputs were generated locally but are not repository artifacts.
The tracked tables below contain the reviewable comparison results.

### Application MV refresh register

Times are from the ordered refresh pass, biggest cost first here for triage; the execution order is
the `Order` column.

| Order | MV | Rows | Refresh | Status / action |
|---:|---|---:|---:|---|
| 1 | `app_v_current_asset_price_mv` | 208 | 1.2 s | Baseline; low priority |
| 2 | `app_v_portfolio_daily_fx_rate_mv` | 1,998 | 5.4 s | Check only if FX fan-out changes |
| 3 | `app_v_normalized_cash_operations` | 19,770 | 113.2 s | P1; inspect FX/classification joins |
| 4 | `app_v_account_monthly` | 215 | 6.7 s | Compare with source-ANALYZE improvement |
| 5 | `app_v_portfolio_monthly` | 21 | 4.6 s | Compare with source-ANALYZE improvement |
| 6 | `app_v_account_statistics` | 11 | 243.7 s | P0; decompose structural work |
| 7 | `app_v_portfolio_contribution_summary_mv` | 1 | 1.1 s | Downstream; do not target first |
| 8 | `app_v_portfolio_currency_breakdown` | 7 | 126.4 s | P1; inspect repeated portfolio/FX branches |
| 9 | `app_v_portfolio_asset_allocation` | 21 | 0.1 s | Low independent cost |
| 10 | `app_v_symbol_performance` | 185 | 98.2 s | P1; reuse price/position inputs |
| 11 | `app_v_portfolio_kpi_summary_mv` | 1 | 3.2 s | Downstream of statistics |

### Reconciliation MV read register

The reconciliation refresh sequence was executed in the requested order. The second diagnostic pass
measured existing-state reads and plans; it intentionally skipped a second refresh. Refresh timings
must therefore be recaptured in a dedicated refresh-only run before comparing reconciliation MV
refresh costs.

| Order | MV | Rows | Read | Plan | Current status |
|---:|---|---:|---:|---:|---|
| 1 | `recon_v_reconstructed_position_daily_mv` | 42,484 | 34 ms | 39 ms | Read healthy; refresh timing pending |
| 2 | `recon_v_reconstructed_account_market_daily_mv` | 3,551 | 10 ms | 9 ms | Read healthy; refresh timing pending |
| 3 | `recon_v_reconstructed_cash_daily_mv` | 5,869 | 13 ms | 14 ms | Read healthy; refresh timing pending |
| 4 | `recon_v_account_daily_reconciliation_mv` | 5,869 | 23 ms | 16 ms | Read healthy; refresh timing pending |
| 5 | `recon_v_account_monthly_profit` | 204 | 12 ms | 8 ms | Read healthy; refresh timing pending |
| 6 | `recon_v_account_statistics_vs_daily` | 11 | 12 ms | 8 ms | Read healthy; refresh timing pending |
| 7 | `recon_v_account_daily_cashflow` | 5,869 | 11 ms | 13 ms | Read healthy; refresh timing pending |
| 8 | `recon_v_account_daily_cashflow_scope` | 5,869 | 16 ms | 12 ms | Read healthy; refresh timing pending |
| 9 | `recon_v_trade_settlement` | 2,633 | 25 ms | 10 ms | Read healthy; refresh timing pending |

### Regular-view triage register

| Priority | View | Result | Plan evidence | Investigation |
|---|---|---|---|---|
| P0 | `app_v_normalized_daily_price` | Read and plan timed out at 45 s | No completed plan | Split candidate-price and latest-date CTEs |
| P0 | `recon_v_validation_issue` | Read and plan timed out at 45 s | No completed plan | Split validation branches and shared inputs |
| P0 | `recon_v_realized_result` | Read and plan timed out at 45 s | No completed plan | Isolate closed-lot, ledger, and FX joins |
| P1 | `recon_v_non_usd_closed_trade` | 22.1 s read / 33.0 s plan | 27.7 s sequential `positions` scan; 664/780 temp blocks | Check join grain and closed-trade staging |
| P1 | `recon_v_asset_price_quality_issues` | 16.2 s read / 17.2 s plan | Repeated 86,567-row `Unique`/sort branches; 7,288 temp blocks | Share canonical-price derivation |
| P1 | `app_v_portfolio_daily` | 3.4 s read / 4.6 s plan | Nested joins and aggregate over 6,089 daily rows | Check repeated cash/FX/valuation work |
| P1 | `app_v_portfolio_performance_daily` | 3.2 s read / 4.4 s plan | Repeated nested-loop/aggregate work | Reuse portfolio-daily inputs |
| P1 | `recon_v_reconciliation_position_issues` | 2.5 s read / 6.7 s plan | Repeated windows over 42,484 position rows; temp spill | Reuse reconstruction/window result |
| P1 | `recon_v_position_valuation_validation` | 2.9 s read / 4.1 s plan | Window/sort over 42,484 rows; temp spill | Check date/position grain |
| P2 | Other completed regular views | See CSV | Mostly sub-second to low-second | Optimize only when consumer fan-out proves value |

### 2026-09-02 component investigation

The first decomposition run measured major input branches separately with a 60-second timeout.
These are component measurements, not proposed semantic changes.

| View / component | Measured evidence | Interpretation |
|---|---|---|
| `app_v_normalized_daily_price` / `position_dates` | 34,364 distinct asset/date keys; 6.2 s | Position lots are expanded across account-day dates before price selection. |
| `app_v_normalized_daily_price` / `price_candidates` | 11,048,870 candidate rows; 18.4 s count | The candidate set is about 322 rows per position/date key before window ranking. This is the primary structural hotspot. |
| `recon_v_validation_issue` / equity and unrealized checks | 0 findings; 479 ms / 421 ms | Not the timeout source. |
| `recon_v_validation_issue` / duplicate prices | 0 findings; 422 ms | Not the timeout source. |
| `recon_v_validation_issue` / duplicate positions | 824 groups; 75 ms | Cheap, but data-quality findings need separate business review. |
| `recon_v_validation_issue` / missing price | timed out at 60 s | Per-open-position/date lateral lookup into canonical prices is the likely dominant branch. |
| `recon_v_validation_issue` / valuation jump | 3,683 candidate rows; 50 ms | Not the timeout source by itself. |
| `recon_v_realized_result` / trade components | 25,617 rows; 84 ms | Base trade-component unions are cheap. Investigate lateral `resolve_fx_rate` and aggregation next. |
| `recon_v_realized_result` / normalized cash input | 19,846 rows; 22 ms | Reading the normalized-cash MV is cheap; full-view cost is downstream computation/joins. |
| `recon_v_non_usd_closed_trade` / closed positions | 1,337 rows; 30 ms | Closed-trade seed set is small. |
| `recon_v_non_usd_closed_trade` / cash join | 367,861 joined rows; 295 ms | Close-day join multiplies rows roughly 275x before grouping; constrain/stage one account/date cash aggregate. |
| `recon_v_asset_price_quality_issues` / included history | 92,597 rows; 178 ms | Base history scan is cheap. |
| `recon_v_asset_price_quality_issues` / canonical prices | 86,567 rows; 965 ms | Canonical source is moderate, but the view references it in multiple branches. |
| `recon_v_asset_price_quality_issues` / source disagreement | 5,503 groups; 715 ms | Grouping is not the main 17-second cost. |
| `recon_v_asset_price_quality_issues` / scale mismatch | 4,586 rows; 270 ms | Data-quality result volume is high but base check is cheap. Repeated canonical/window branches drive the plan. |

### Price-shape probe

An indexed-lateral shape probe was tested without changing repository SQL. Looking up a price through
the existing `app_v_canonical_asset_daily_price` view took 117.4 s because the
canonical `DISTINCT ON` view was recomputed for each of 34,364 position/date keys. Building that
canonical relation once in a temporary indexed relation reduced the probe to 14.3 s. The probe used
a simplified price ordering, so 14.3 s is architecture evidence, not a correctness result. The exact
current selection output must be compared for all keys before changing the schema. A lateral rewrite
without such a reusable relation is rejected as a regression.

### Revised optimization order

1. `app_v_normalized_daily_price`: reduce the 11.0-million-row candidate expansion before tuning
   ranking. Evaluate an indexed latest-observation strategy or a reusable per-asset/date price
   spine, preserving the exact freshness/quality/interpolation ordering contract.
2. `recon_v_validation_issue` missing-price branch: reuse the normalized daily-price result or a
   single latest-price relation keyed by `(asset_id, snapshot_date)` instead of invoking a lateral
   lookup for every open position/date pair.
3. `recon_v_asset_price_quality_issues`: compute canonical prices once per query and share that
   relation across extreme-move and interpolation checks; do not optimize the cheap base scans first.
4. `recon_v_non_usd_closed_trade`: aggregate normalized cash by `(account_id, date)` before joining
   to closed trades; verify that this preserves the diagnostic's non-trade-flow semantics.
5. `recon_v_realized_result`: instrument `resolve_fx_rate` calls and the grouped converted-trade
   branch. The raw trade/cash inputs do not explain the timeout.
6. Re-run the full views with endpoint predicates and the same settings. Full unfiltered reads are
   useful worst-case evidence, but they are not a substitute for application query latency.

## Optimization tracking plan

| ID | Work item | Priority | Success evidence | Status |
|---|---|---|---|---|
| OPT-01 | Capture dedicated reconciliation-MV refresh timings | P0 | Ordered per-MV refresh CSV with settings | Open |
| OPT-02 | Decompose `app_v_account_statistics` by CTE/branch | P0 | Actual rows, buffers, temp I/O, JIT and branch time | Open |
| OPT-03 | Decompose normalized cash and FX resolution | P1 | Cardinality and repeated lookup evidence | Open |
| OPT-04 | Measure canonical price candidate/ranking waste | P1 | Rows entering/leaving each ranking stage | Open |
| OPT-05 | Investigate repeated reconstruction/window work | P1 | Shared-input versus current-plan comparison | Open |
| OPT-06 | Break down the three timeout views | P0 | Component plans complete under bounded timeout | Open |
| OPT-07 | Test one narrow SQL/materialization/index change at a time | P1 | Before/after same-data, same-settings run | Not started |
| OPT-08 | Re-run the complete ordered baseline after changes | P0 | Refresh, read, plan, and data-correctness comparison | Not started |

### Implementation checkpoint — 2026-09-02

The applied `V01.0100`–`V01.0103` changes are squashed into the original schema, function, price-history,
portfolio-view, and reconciliation-view migrations. The indexed canonical daily-price MV, bounded
current-price MV, and portfolio/date/currency FX MV are now created at their original object locations;
the public names remain compatibility views. `PortfolioProjectionRefreshService` and
`refresh_app_views()` refresh the canonical input before dependent application MVs. The exact live-profile
comparison passed with 34,364 candidate rows and zero field mismatches. Clean migration validation and
snapshot regeneration passed on 2026-09-02.

### Decision rules

- Keep source-table `ANALYZE` before refresh; it fixes estimate/JIT cliffs but not structural work.
- Do not add indexes or rewrite schemas from planner cost alone; first prove actual row multiplication,
  buffers, temp I/O, JIT, and repeated computation.
- Prefer reusable refresh-cycle inputs for normalized cash, FX, canonical prices, and reconstruction
  windows when their freshness and domain semantics remain unchanged.
- Recheck correctness and reconciliation counts after every optimization candidate.

### Live profile follow-up — 2026-09-02, `.60` database

The application was already running and Flyway was healthy, so this run used existing MV state
and did not refresh production-like objects. All materialized-view reads and plans completed quickly:
current price 13 ms, portfolio FX 10 ms, normalized cash 30 ms, account statistics 22 ms, and
reconciliation MVs 8–37 ms.

The remaining regular-view bottlenecks are:

| View | Read | `EXPLAIN ANALYZE` | Rows | First finding |
|---|---:|---:|---:|---|
| `app_v_normalized_daily_price` | 45.1 s timeout | 45.2 s timeout | n/a | canonical price selection still recomputed across the position/date shape |
| `recon_v_realized_result` | 45.0 s timeout | 45.0 s timeout | n/a | requires component decomposition before rewrite |
| `recon_v_portfolio_service_fallback` | 15 ms | 45.0 s timeout | 1 | planner execution spends time in a branch not reflected by returned rows |
| `recon_v_non_usd_closed_trade` | 21.0 s | 32.9 s | 1,353 | `positions` sequential scan ~27.8 s; 661 temp blocks read / 781 written |
| `recon_v_validation_issue` | 12.3 s | 14.6 s | 8 | append branches spill: 410 temp blocks read / 760 written |
| `recon_v_asset_price_quality_issues` | 13.1 s | 13.4 s | 4,679 | append branches spill: 4,774 temp blocks read / 3,924 written |

Next optimization experiment: isolate `app_v_normalized_daily_price` into reusable canonical-price
input plus date expansion, compare exact output, then address the sequential `positions` scan in
`recon_v_non_usd_closed_trade`. Do not change the three timeout views together.

The first normalized-price probe confirms the mechanism. A lateral winner lookup without an order
index returned all 34,364 keys in 59.8 s. The same lookup over a temporary canonical relation carrying
`effective_observation_date` and `selection_priority`, indexed by asset/effective date/order, returned
the same 34,364 keys in 11.0 s. The earlier exact field comparison against the current selector had
zero mismatches. The implementation candidate is therefore an indexed canonical input plus a one-pass
date expansion; it still needs a normal forward migration for already-applied databases.

`V01.0104__index_normalized_daily_price_selection.sql` creates the ranked canonical input and
`V01.0105__optimize_normalized_daily_price_lookup.sql` switches `app_v_normalized_daily_price` to
the indexed lateral winner lookup. The same ranked MV and lookup are present in the clean baseline and
application refresh order. `V01.0104` and `V01.0105` applied successfully to the disposable clean
database. `V01.0106__scope_normalized_price_dates.sql` now applies the verified account-scoped date
join; live `.60` needs the next application restart for this latest migration.

Post-`V01.0105` verification on the live `.60` database found the ranked MV populated (86,703 rows)
and the new order index in use. However, `app_v_normalized_daily_price` still exceeded 60 s, including
with `jit=off`. The plan now performs an indexed lookup, but the `position_dates` stage still drives
34,364 keys and costs about 6.7 s by joining positions to the global distinct date set; the next
experiment is to remove that avoidable global date expansion and preserve exact output counts.

The key-set probe passed: global-date and account-scoped-date forms both returned 34,364 keys, with
zero `global_only` and zero `scoped_only` rows. The account-scoped join is the selected next change;
after restart, measure it before changing the price winner logic again.

`V01.0107__materialize_normalized_daily_price.sql` is now prepared. It materializes the expensive
selector directly (no MV/view dependency loop) and keeps `app_v_normalized_daily_price` as a public
compatibility wrapper. Clean migration and snapshot generation pass; the live `.60` database needs
this migration applied before the next timing run.

The next live probe separated JIT overhead from relational work. `recon_v_non_usd_closed_trade` returned
1,353 rows in 21.3 s with session `jit=on`, but 4.8 s with `jit=off`; the standalone `positions` scan
was only about 20 ms. This is a query-complexity/JIT cliff, not a missing `positions` index. In contrast,
`recon_v_validation_issue` was about 13.4 s with JIT on and 13.6 s with JIT off, so its cost is genuine
append/branch work and temp spill. Do not add a positions index for the closed-trade symptom; simplify
or isolate that view first.

### Next optimization step — realized-result FX fan-out

`recon_v_realized_result` remains the next regular-view target. The live database has 25,617
trade-component rows but only about 489 distinct `(valuation_date, source_currency, base_currency)`
resolver keys. `V01.0108__deduplicate_realized_result_fx.sql` resolves FX once per key and joins the
resolved result back to the components. The focused clean-database materialized-view contract passed
after adding this migration (6 tests, 0 failures). Apply it to live `.60`, then remeasure the view and
compare result rows and all numeric columns before considering further changes.

Live `.60` verification after manual application of `V01.0109`: `recon_v_realized_result` returned
1,643 rows in 2.5 s, with zero incomplete rows. `EXPLAIN ANALYZE` completed in 2,500 ms and showed
separate `trade_fx_keys` and `resolved_trade_fx` CTEs, confirming that the resolver branch is now
materialized before component aggregation. Flyway will record `V01.0109` on the next application
restart; the current live DDL was applied manually without changing data.

## Source reports

- [Empty/near-empty materialized-view baseline](../../reconciliation/local-profile-materialized-view-performance-baseline-2026-09-01.md)
- [Populated remote profile analysis](../../reconciliation/local-profile-remote-mv-performance-analysis-2026-09-01.md)

## 2026-09-02 rerun: before/after, biggest current time first

Live `.60` rerun settings: JDBC, PostgreSQL 14.23, `jit=on`, `work_mem=4MB`. MV rows were read with
`SELECT count(*)`; MV refresh time includes `REFRESH MATERIALIZED VIEW` plus `ANALYZE`. View rows were
read with the same count query. Plan time used bounded `EXPLAIN ANALYZE` with a 50-second timeout.

| Current ms | Object | Operation | Before ms | After ms | Result |
|---:|---|---|---:|---:|---|
| 104,261 | `app_v_portfolio_currency_breakdown` MV | refresh | 126,400 | 104,261 | -17.5% |
| 101,747 | `app_v_account_statistics` MV | refresh | 243,700 | 101,747 | -58.2% |
| 87,642 | `app_v_symbol_performance` MV | refresh | 98,200 | 87,642 | -10.7% |
| 77,479 | `app_v_normalized_daily_price_mv` MV | refresh | n/a | 77,479 | new MV |
| 51,569 | `recon_v_reconstructed_cash_daily_mv` MV | refresh | n/a | 51,569 | first refresh baseline |
| 51,219 | `app_v_normalized_cash_operations` MV | refresh | 113,200 | 51,219 | -54.8% |
| 21,767 | `recon_v_non_usd_closed_trade` view | read | 22,100 | 21,767 | -1.5%; still JIT-sensitive |
| 20,590 | `recon_v_reconstructed_position_daily_mv` MV | refresh | n/a | 20,590 | first refresh baseline |
| 18,875 | `recon_v_account_daily_reconciliation_mv` MV | refresh | n/a | 18,875 | first refresh baseline |
| 16,787 | `recon_v_trade_settlement` MV | refresh | n/a | 16,787 | first refresh baseline |
| 14,707 | `recon_v_validation_issue` view | plan | >45,000 | 14,707 | still append/temp-spill hotspot |
| 13,489 | `recon_v_asset_price_quality_issues` view | read | 16,200 | 13,489 | -16.7% |
| 12,294 | `recon_v_validation_issue` view | read | 12,300 | 12,294 | unchanged |
| 5,584 | `app_v_portfolio_daily_fx_rate_mv` MV | refresh | 5,400 | 5,584 | +3.4% |
| 4,749 | `app_v_portfolio_daily` view | plan | 4,600 | 4,749 | +3.2% |
| 3,562 | `recon_v_reconciliation_position_issues` view | read | 2,500 | 3,562 | +42.5% |
| 3,527 | `app_v_portfolio_performance_daily` view | read | 3,200 | 3,527 | +10.2% |
| 3,500 | `app_v_account_monthly` MV | refresh | 6,700 | 3,500 | -47.8% |
| 3,391 | `app_v_portfolio_monthly` MV | refresh | 4,600 | 3,391 | -26.3% |
| 3,231 | `recon_v_position_valuation_validation` view | read | 2,900 | 3,231 | +11.4% |
| 3,148 | `app_v_portfolio_daily` view | read | 3,400 | 3,148 | -7.4% |
| 3,132 | `app_v_portfolio_kpi_summary_mv` MV | refresh | 3,200 | 3,132 | -2.1% |
| 3,093 | `app_v_canonical_asset_daily_price_mv` MV | refresh | n/a | 3,093 | new MV |
| 2,867 | `recon_v_realized_result` view | plan | >45,000 | 2,867 | timeout fixed |
| 2,331 | `recon_v_realized_result` view | read | >45,000 | 2,331 | timeout fixed |
| 124 | `recon_v_reconstructed_cash_daily_mv` MV | read | 13 | 124 | refreshed-state variation |
| 97 | `app_v_portfolio_asset_allocation` MV | refresh | 100 | 97 | stable |
| 23 | `app_v_normalized_daily_price` view | plan | >45,000 | 23 | timeout fixed by MV wrapper |
| 20 | `app_v_normalized_daily_price` view | read | >45,000 | 20 | timeout fixed by MV wrapper |

The full rerun also measured all 23 tracked MVs and 10 regular views; the table shows the material
costs and all current regular-view hotspots. MV refreshes changed MV contents only; base tables were
not changed. The biggest wins are normalized daily price (`>45 s` to 20 ms), realized result (`>45 s`
to 2.3 s), account statistics refresh (-58%), and normalized cash refresh (-55%). Next target remains
`recon_v_validation_issue`, followed by `recon_v_asset_price_quality_issues`.

### Focused analysis: the three remaining regular-view hotspots

#### `recon_v_non_usd_closed_trade`

Current read time is 21.8 s and the current plan time is 31.9 s. The closed-position seed is small
(1,353 rows); the earlier branch probe found the standalone closed-position scan at about 30 ms and
the close-day cash join at 367,861 rows before grouping. The main problems are therefore not a
missing `positions` index:

- `day_cash_other_flows` joins every selected closed position to all normalized cash rows for the
  account/date and groups afterward. This creates roughly 275 joined rows per closed position in the
  observed data.
- `close_day_account` applies account-wide `LAG` windows after joining through closed positions,
  repeating account-day work for each selected position.
- `fx_at_close` invokes `resolve_fx_rate` twice per closed position. The earlier controlled probe was
  21.3 s with JIT on and 4.8 s with JIT off, confirming a JIT/query-complexity cliff.

Recommended fix order: pre-aggregate normalized cash once by `(account_id, date)`; build one account-day
window relation and join it to closed positions; then deduplicate close FX keys before resolution. Do
not add a `positions` index for this symptom. Preserve the diagnostic row grain, which is one row per
closed position.

#### `recon_v_validation_issue`

Current read time is 12.3 s and plan time is 14.7 s, with little JIT difference in the earlier probe.
The cheap branches are equity/unrealized checks, duplicate prices, duplicate positions, valuation jump,
and unclassified cash. The dominant branch is `missing_price`:

- it expands open positions across distinct account-day dates;
- it performs a lateral latest-price lookup for every position/date pair;
- the branch returned no findings in the observed data, but still pays the full lookup cost;
- the other `UNION ALL` branches cannot explain the runtime by themselves.

The first candidate (reuse of `app_v_normalized_daily_price_mv`) was rejected: it changed the
observed issue set from 8 rows to 212 because normalized prices include fallback rows that this
validation contract must not treat as canonical. The accepted rewrite keeps the canonical-price
semantics, reads the indexed canonical MV directly, and changes the valuation-jump trade-notional
join from an account-wide date join to exact open/close event dates.

#### `recon_v_asset_price_quality_issues`

Current read time is 13.5 s and plan time is 14.3 s. Base scans are not large enough to explain this:
included history is about 92,597 rows and canonical prices about 86,567 rows. The cost comes from
repeated branch work and temp spill:

- `included_history` is referenced by currency mismatch, source disagreement, interpolation, and
  scale-mapping branches, so the same history relation can be rescanned or re-planned;
- canonical prices are used once for same-date source comparison and again for the windowed extreme
  move check;
- interpolation performs per-row lateral lookups into canonical prices;
- the earlier plan spilled about 4,774 temp blocks read and 3,924 written.

Recommended fix: first materialize one shared `included_history` relation and one shared canonical
price relation inside the query, then compare runtime and exact issue output. The source-disagreement
and scale-mismatch aggregates are not the first targets; they were individually sub-second in the
component probe.

### Focused next-step order

1. Keep and monitor the `recon_v_validation_issue` rewrite below; its remaining cost is mostly
   unavoidable validation scans and duplicate-price grouping.
2. Rewrite `recon_v_non_usd_closed_trade` cash and account-day branches, then test JIT on/off again.
3. Materialize shared CTE inputs in `recon_v_asset_price_quality_issues` and compare issue equality.

Each change should be a separate migration with same-data row/value comparison before and after.

### 2026-09-03 `recon_v_non_usd_closed_trade` rewrite

Migration `V01.0111__optimize_non_usd_closed_trade.sql` was applied to local profile `.60` after
clean-database migration tests reached v01.0111 repeatedly. It uses an indexed preceding-day lookup
and resolves FX once per distinct `(close_date, source_currency, base_currency)` key. The live view
returned 1,353 rows after the rewrite, matching the previously observed result count.

| Metric | Before | After | Result |
|---|---:|---:|---|
| Read (`SELECT count(*)`) | 21.8 s | 15.8 s | about 28% faster |
| Full `EXPLAIN ANALYZE SELECT *` | 31.9 s | 24.5 s | about 23% faster |
| Distinct FX keys | ~1,182 function misses | 150 keys | FX work reduced materially |
| Previous-day join | ~368k intermediate rows | 1,337 indexed lookups | range-join expansion removed |

Exact old/new comparison passed on the repaired host: `old=1353`, `new=1353`, `old_only=0`,
`new_only=0` across all output columns. The checkpoint suite again has only the unrelated
`currentPositionCostBasisUsesAcquisitionDateFx` failure (`2000` actual vs `1100` expected).
### 2026-09-03 validation-issue rewrite

Migration `V01.0110__reuse_normalized_price_in_validation.sql` was applied manually to the local
profile `.60` database after the normalized-price candidate was rejected for semantic drift. The
final version uses `app_v_canonical_asset_daily_price_mv` through its `(asset_id, price_date)` index
for the missing-price lookup and pre-aggregates position open/close events before joining to
`account_daily`.

| Metric | Before | After | Result |
|---|---:|---:|---|
| `recon_v_validation_issue` rows | 8 | 8 | exact row count preserved |
| Read (`SELECT count(*)`) | 12.3 s | 3.2 s | about 74% faster |
| `EXPLAIN ANALYZE SELECT *` | 14.7 s | 2.86 s | about 81% faster |
| Validation issue differences | n/a | 0 observed | semantic columns matched during candidate check |

The migration test applied v01.0110 repeatedly on clean databases without migration errors. The
full checkpoint run still reports one unrelated existing failure in
`currentPositionCostBasisUsesAcquisitionDateFx` (`2000` actual vs `1100` expected); it is not caused
by this view migration.

### 2026-09-03 `recon_v_non_usd_closed_trade` deep probe

The live `.60` database contains 1,337 target closed trades, 19,846 normalized cash-operation
rows, and 6,089 account-day rows. The earlier full-view baseline remains about 21.8 s for the read
and 31.9 s for the full plan/execution. Focused probes identify the following costs:

| Component | Observed time | Evidence |
|---|---:|---|
| FX at close, two `resolve_fx_rate` calls per trade | 3.67 s | 591 distinct function misses per currency/date key; each function rescans `exchange_rates` and builds FX edges; JIT alone took about 1.51 s |
| Previous trade day and previous symbol value | 1.16 s | 368,476 account-day/closed-position join rows before grouping; 421,866 rows removed by the date filter |
| Previous trade-day lookup alone | 0.61 s | 184,238 join rows per parallel worker before grouping |
| Close-day account values and `LAG` | 49 ms | Exact account/date join; not a bottleneck |
| Other close-day cash flows | 132 ms | 17,294 joined rows, then 1,337 position groups; not a bottleneck |

The plan also scans all 9,206 positions to identify the 1,337 target trades. The first rewrite should
therefore not focus on cash. It should build one compact closed-trade key set, resolve FX once per
distinct `(close_date, source_currency, base_currency)` key, and derive each position's previous
account day without joining every closed trade to every earlier account day. Then compare the full
view output, including FX status and anomaly codes, before applying a migration.

### 2026-09-03 `recon_v_asset_price_quality_issues` rewrite

Migration `V01.0112__optimize_asset_price_quality_issues.sql` was applied to local profile `.60`
after clean-database migration tests reached v01.0112. It materializes the shared included-history
input once, evaluates daily-move thresholds once per statement, and reads the canonical price MV
directly. Exact comparison against the original view passed across every output column.

| Metric | Before | After | Result |
|---|---:|---:|---|
| Read (`SELECT count(*)`) | 13.5 s | 6.1 s | about 55% faster |
| Full `EXPLAIN ANALYZE SELECT *` | 14.3 s | 5.55 s | about 61% faster |
| Output rows | 4,679 | 4,679 | preserved |
| Output differences | n/a | 0 | exact `EXCEPT ALL` comparison |

The remaining plan cost is mostly the intentional scans/grouping over 92,733 included history rows
and 86,703 canonical rows. Temp spill remains visible during the source-disagreement aggregate and
scale-mapping check; it is now a secondary target rather than the first rewrite target.

## 2026-09-03 complete live baseline

The full baseline was rerun against local profile `.60` using PostgreSQL 14.23, `jit=on`,
`work_mem=4MB`, and the local JDBC profile. Application MVs were refreshed first in
`PortfolioProjectionRefreshService.APPLICATION_FULL_ORDER` order. Reconciliation MVs were refreshed
second in `PortfolioProjectionRefreshService.RECONCILIATION_ORDER`: reconstructed position, account
market, reconstructed cash, account reconciliation, then reporting MVs. Each refresh includes
`ANALYZE`. Regular-view reads use `SELECT count(*)`; plans use
`EXPLAIN (ANALYZE, BUFFERS, SUMMARY, SETTINGS) SELECT *` with a 180-second timeout.

### MV refreshes, sorted by current time

| Current refresh | MV | Rows | Earlier comparable |
|---:|---|---:|---:|
| 107.6 s | `app_v_portfolio_currency_breakdown` | 7 | 104.3 s |
| 106.1 s | `app_v_account_statistics` | 11 | 101.7 s |
| 89.6 s | `app_v_symbol_performance` | 185 | 87.6 s |
| 77.6 s | `app_v_normalized_daily_price_mv` | 34,364 | 77.5 s |
| 54.3 s | `app_v_normalized_cash_operations` | 19,846 | 51.2 s |
| 52.9 s | `recon_v_reconstructed_cash_daily_mv` | 6,089 | 51.6 s |
| 20.1 s | `recon_v_reconstructed_position_daily_mv` | 42,456 | 20.6 s |
| 18.8 s | `recon_v_account_daily_reconciliation_mv` | 6,089 | 18.9 s |
| 17.0 s | `recon_v_trade_settlement` | 2,688 | 16.8 s |
| 6.1 s | `app_v_canonical_asset_daily_price_ranked_mv` | 86,703 | n/a |
| 5.8 s | `recon_v_account_daily_cashflow` | 6,089 | n/a |
| 5.6 s | `app_v_portfolio_daily_fx_rate_mv` | 2,001 | 5.6 s |
| 4.3 s | `app_v_canonical_asset_daily_price_mv` | 86,703 | 3.1 s |
| 3.8 s | `app_v_account_monthly` | 215 | 3.5 s |
| 3.7 s | `app_v_portfolio_kpi_summary_mv` | 1 | 3.1 s |
| 3.5 s | `app_v_portfolio_monthly` | 21 | 3.4 s |
| 1.0 s | `app_v_portfolio_contribution_summary_mv` | 1 | n/a |
| 0.5 s | `recon_v_account_daily_cashflow_scope` | 6,089 | n/a |
| 0.5 s | `recon_v_account_monthly_profit` | 215 | n/a |
| 0.3 s | `recon_v_reconstructed_account_market_daily_mv` | 3,691 | n/a |
| 0.2 s | `app_v_current_asset_price_mv` | 208 | 0.2 s |
| 0.2 s | `app_v_portfolio_asset_allocation` | 18 | 0.1 s |
| 0.3 s | `recon_v_account_statistics_vs_daily` | 11 | n/a |

The largest remaining refresh costs are the three application reporting MVs, normalized daily
price/cash inputs, and reconstructed cash. These are refresh costs; they do not imply a slow regular
view read.

### Regular views, sorted by current plan time

| Current plan | View | Rows | Current read | Earlier read / plan |
|---:|---|---:|---:|---:|
| 105.2 s | `recon_v_portfolio_service_fallback` | 1 | 0.01 s | 0.02 s / timeout |
| 26.3 s | `recon_v_non_usd_closed_trade` | 1,353 | 15.8 s | 21.8 s / 31.9 s |
| 6.6 s | `recon_v_reconciliation_position_issues` | 6,604 | 3.2 s | 3.6 s / 6.4 s |
| 6.3 s | `recon_v_asset_price_quality_issues` | 4,679 | 4.6 s | 13.5 s / 14.3 s |
| 4.5 s | `app_v_portfolio_daily` | 666 | 2.9 s | 3.1 s / 4.7 s |
| 4.1 s | `app_v_portfolio_performance_daily` | 604 | 3.7 s | 3.5 s / 3.7 s |
| 3.9 s | `recon_v_position_valuation_validation` | 6,604 | 2.9 s | 3.2 s / 3.4 s |
| 3.2 s | `recon_v_validation_issue` | 8 | 3.3 s | 12.3 s / 14.7 s |
| 3.0 s | `recon_v_realized_result` | 1,643 | 1.9 s | 2.3 s / 2.9 s |
| 0.02 s | `app_v_normalized_daily_price` | 34,364 | 0.02 s | 0.02 s / 0.02 s |

The two accepted view rewrites remain effective: validation is about 73% faster on read and
non-USD closed-trade planning is about 18% faster than the immediately previous baseline. The
service-fallback view is now the largest regular-view plan outlier, but its read is effectively free;
inspect it only if a consumer requests its full plan frequently. No UI test was included in this
DB-only baseline.

### 2026-09-03 priority-plan analysis

A focused live probe was run against profile `.60` with the same `work_mem=4MB` and PostgreSQL
14.23. The result changes the priority for `recon_v_non_usd_closed_trade`:

| Probe | JIT | Rows | Execution time | Finding |
|---|---:|---:|---:|---|
| `SELECT count(*) FROM recon_v_non_usd_closed_trade` | on | 1,353 | 15.2 s | JIT compilation dominates the candidate-set scan |
| Same query | off | 1,353 | 0.34 s | Same result; relational work is small |

With JIT enabled, the `positions` scan reported about 24.5 s in the full `SELECT *` plan despite
only 342 shared buffers and 9,206 input rows. With JIT disabled, that scan completed in about
20 ms and the whole count completed in about 0.34 s. The existing `account_daily` index and the
reconstructed-position MV unique index are being used; the cash-flow branch is only about 0.17 s.
Adding more indexes to this view is therefore not the next step.

The two largest MVs remain structural refresh candidates. Their definitions independently walk
closed/open positions, normalized cash, and row-level `resolve_fx_rate` calls, so they may repeat
the same FX and transaction work. A refresh-time JIT comparison should be run before rewriting
either MV. No new schema or query rewrite was applied from this probe.

Recommended next action: test `jit=off` for the MV refresh session and for the application read
session in a controlled comparison. If refresh and reads improve without changing result sets,
prefer a narrow session/database setting over another view rewrite. Only then investigate shared
FX/position aggregation for `app_v_account_statistics` and
`app_v_portfolio_currency_breakdown`.

The controlled `jit=off` refresh probe was inconclusive: `REFRESH MATERIALIZED VIEW CONCURRENTLY`
for `app_v_portfolio_currency_breakdown` remained CPU-active beyond the 60-second runner window,
without waiting on a lock, and was cancelled. No refresh-time speedup is claimed from that attempt;
the live MV was left under the normal application refresh contract.

The longer controlled rerun completed successfully with the ordinary refresh command:

| MV | JIT | Rows | Refresh + analyze | Baseline | Change |
|---|---:|---:|---:|---:|---:|
| `app_v_account_statistics` | off | 11 | 85.1 s | 106.1 s | 19.8% faster |
| `app_v_portfolio_currency_breakdown` | off | 7 | 90.0 s | 107.6 s | 16.3% faster |

Both result counts remained unchanged. This confirms JIT overhead affects MV refreshes as well as
the non-USD diagnostic view. The safest optimization is a narrow `jit=off` setting for the
application connection pool or refresh session, not a global PostgreSQL change. The setting should
be validated against the full application/MV refresh contract before being committed.

### 2026-09-03 `jit=off` refresh implementation

`PortfolioProjectionRefreshService` now executes `SET LOCAL jit=off` inside each MV refresh
transaction. This scopes the setting to the refresh and `ANALYZE`; normal application reads keep
the database default. The complete live order was rerun against profile `.60`: application MVs
first, then reconciliation MVs. All 23 refreshes committed and each MV returned its expected
non-zero row count.

| Plan | MV | Refresh time | Rows |
|---|---|---:|---:|
| application | `app_v_canonical_asset_daily_price_mv` | 6.1 s | 86,703 |
| application | `app_v_canonical_asset_daily_price_ranked_mv` | 6.2 s | 86,703 |
| application | `app_v_normalized_daily_price_mv` | 91.6 s | 34,364 |
| application | `app_v_current_asset_price_mv` | 0.2 s | 208 |
| application | `app_v_portfolio_daily_fx_rate_mv` | 5.7 s | 2,001 |
| application | `app_v_normalized_cash_operations` | 56.3 s | 19,846 |
| application | `app_v_account_monthly` | 4.0 s | 215 |
| application | `app_v_portfolio_monthly` | 3.1 s | 21 |
| application | `app_v_account_statistics` | 86.3 s | 11 |
| application | `app_v_portfolio_contribution_summary_mv` | 0.3 s | 1 |
| application | `app_v_portfolio_currency_breakdown` | 88.9 s | 7 |
| application | `app_v_portfolio_asset_allocation` | 0.1 s | 18 |
| application | `app_v_symbol_performance` | 80.6 s | 185 |
| application | `app_v_portfolio_kpi_summary_mv` | 3.1 s | 1 |
| reconciliation | `recon_v_reconstructed_position_daily_mv` | 20.8 s | 42,456 |
| reconciliation | `recon_v_reconstructed_account_market_daily_mv` | 0.3 s | 3,691 |
| reconciliation | `recon_v_reconstructed_cash_daily_mv` | 52.5 s | 6,089 |
| reconciliation | `recon_v_account_daily_reconciliation_mv` | 17.8 s | 6,089 |
| reconciliation | `recon_v_account_monthly_profit` | 0.5 s | 215 |
| reconciliation | `recon_v_account_statistics_vs_daily` | 0.2 s | 11 |
| reconciliation | `recon_v_account_daily_cashflow` | 6.5 s | 6,089 |
| reconciliation | `recon_v_account_daily_cashflow_scope` | 0.5 s | 6,089 |
| reconciliation | `recon_v_trade_settlement` | 13.9 s | 2,688 |

Compared with the prior baseline, account statistics improved 106.1 s -> 86.3 s, currency
breakdown 107.6 s -> 88.9 s, and symbol performance 89.6 s -> 80.6 s. Normalized price was
slower in this run (77.6 s -> 91.6 s), so JIT-off helps the largest reporting MVs but is not a
guaranteed improvement for every MV.

Focused verification passed:

- `PortfolioProjectionRefreshServiceTest`: 1 test, 0 failures.
- `MaterializedViewRefreshContractIT`: 6 tests, 0 failures.
- Reactor build completed successfully.

### 2026-09-03 shared FX-map analysis

The existing `app_v_portfolio_daily_fx_rate_mv` was compared with direct
`resolve_fx_rate(date, source_currency, base_currency)` results for all distinct closed-position
keys used by the large reporting MVs. The live comparison found 494 keys, 494 exact matches, and
0 differences, including rate and conversion status. The shared FX MV can therefore replace the
repeated closed-position resolver calls in `app_v_account_statistics`,
`app_v_portfolio_currency_breakdown`, and `app_v_symbol_performance`.

This is a good next structural optimization, but it is not a safe one-line edit: PostgreSQL does
not support `CREATE OR REPLACE MATERIALIZED VIEW`, and these MVs have downstream indexes and KPI
dependencies. The implementation needs an append-only migration with dependency-aware MV rebuilds,
then exact output comparisons. Do not use an unqualified `DROP ... CASCADE` migration.

### 2026-09-03 temporary shared-FX version test

A session-scoped temporary comparison was run on profile `.60` before changing any permanent MV
definition. It built the common closed-position FX component rows twice: once joining the existing
`app_v_portfolio_daily_fx_rate_mv`, and once calling `resolve_fx_rate` directly.

| Check | Result |
|---|---:|
| Shared FX build | 0.24 s |
| Direct resolver build | 79.7 s |
| Component rows on each side | 2,674 |
| Row-level differences | 0 |
| Account aggregate differences | 0 |
| Portfolio/currency aggregate differences | 0 |
| Portfolio/asset aggregate differences | 0 |

The temporary tables were rolled back and no permanent MV definition was changed. This confirms
that a shared FX join can remove the repeated closed-position resolver cost while preserving the
account, currency, and symbol aggregation results. The next implementation is the dependency-aware
append-only MV rebuild described above.

### 2026-09-03 first permanent shared-FX rebuild

Migration `V01.0113__reuse_portfolio_fx_map_in_symbol_performance.sql` rebuilds the independent
`app_v_symbol_performance` MV and replaces its closed-position `resolve_fx_rate` calls with joins
to `app_v_portfolio_daily_fx_rate_mv`. It was applied transactionally on profile `.60`; the old
and new MV outputs matched exactly across all business columns, with 185 rows preserved.

This is the first permanent slice. Account statistics and currency breakdown remain unchanged
until their dependent KPI/reporting objects are rebuilt in a separate migration.

### 2026-09-03 account-statistics shared-FX rebuild

Migration `V01.0114__reuse_portfolio_fx_map_in_currency_breakdown.sql` was applied before this
slice and preserved all 7 currency-breakdown rows exactly. Migration
`V01.0115__reuse_portfolio_fx_map_in_account_statistics.sql` then rebuilt
`app_v_account_statistics` and `app_v_portfolio_kpi_summary_mv`, replacing the two remaining
closed-position and latest-account FX resolver paths with indexed joins to
`app_v_portfolio_daily_fx_rate_mv`.

The migration captured and restored dependent reconciliation objects in dependency order:
`recon_v_account_statistics_vs_daily`, `recon_v_portfolio_account_quality`,
`recon_v_portfolio_data_quality`, `recon_v_portfolio_service_fallback`, and the compatibility
view `app_v_portfolio_kpi_summary`. It also restored the dependent MV index. No `DROP ... CASCADE`
was used.

Live profile `.60` verification:

| Object | Rows after rebuild | Equality result |
|---|---:|---|
| `app_v_account_statistics` | 11 | exact business-column match; 0 differences |
| `app_v_portfolio_kpi_summary_mv` | 1 | exact business-column match; 0 differences |
| `app_v_account_statistics_reporting` | 11 | queryable |
| `recon_v_account_statistics_vs_daily` | 11 | queryable |
| `recon_v_portfolio_account_quality` | 11 | queryable |
| `recon_v_portfolio_data_quality` | 1 | queryable |
| `recon_v_portfolio_service_fallback` | 1 | queryable |
| `app_v_portfolio_kpi_summary` | 1 | queryable |

The focused application contract was retried after normalizing the snapshot and replacing the
single-row `portfolios` COPY fixture with an equivalent INSERT. The remaining checkout blocker is
earlier dirty-worktree test compilation in `BenchmarkServiceTest` and `MarketDataServiceTest`
(stale method signatures), so the app contract did not execute in the reactor. The broader reactor
also has two unrelated notification-test failures. The live transactional migration and all
dependent-object query checks passed; clean application-contract validation remains follow-up work.

### 2026-09-03 complete post-optimization live baseline

After the shared-FX rebuilds, the complete refresh order was rerun on profile `.60` with
`jit=off` on each refresh connection. Read time below is a full `SELECT count(*)` scan after the
refresh. Rows follow the actual application-then-reconciliation execution order; the dominant
refreshes are called out below.

### 2026-09-03 remaining bottleneck investigation

The six slowest refreshes were checked on live profile `.60` with
`EXPLAIN (ANALYZE, BUFFERS)`. Their materialized-view reads are not bottlenecked: full sequential
scans completed in approximately 2.5–31.6 ms. The expensive work is therefore in the defining
queries executed during refresh, not in request-time MV reads.

The next strong candidate is `app_v_normalized_cash_operations`. Its `port_resolved` CTE calls
`resolve_portfolio_fx_rate` for each distinct portfolio/date/currency key even though
`app_v_portfolio_daily_fx_rate_mv` already stores that map. A live temporary comparison found:

| Candidate path | Time | Keys | Differences |
|---|---:|---:|---:|
| Direct `resolve_portfolio_fx_rate` | 2.652 s | 762 | baseline |
| Shared FX MV join | 0.019 s | 762 | 0 rate/status differences |

This is an estimated 140x improvement for that FX substep and preserves both rate and conversion
status exactly. It is suitable for a temporary full-MV candidate rebuild next. The remaining
normalized-cash `acct_resolved` and `txn_resolved` paths must remain unchanged until separately
compared, because they use account-currency and transaction-specific semantics rather than the
portfolio-base FX map.

`app_v_normalized_daily_price_mv` remains the largest refresh at 90.839 s. Its defining query
expands position/date pairs and performs an ordered lateral lookup into 86,703 ranked price rows;
the next experiment should target that lookup/index shape. Reconstructed cash remains 53.191 s and
is dominated by cumulative cash aggregation over valuation dates, so it should be investigated
after the normalized-cash shared-FX slice.

### 2026-09-03 permanent normalized-cash shared-FX rebuild

Migration `V01.0116__reuse_portfolio_fx_map_in_normalized_cash.sql` replaces only the
`port_resolved` resolver path in `app_v_normalized_cash_operations` with a join to
`app_v_portfolio_daily_fx_rate_mv`. The migration captures and restores the complete dependent
application/reconciliation view and MV closure, including dependent MV indexes, without
`DROP ... CASCADE`.

Live profile `.60` verification passed transactionally:

| Check | Result |
|---|---:|
| Normalized-cash rows after rebuild | 19,846 |
| Exact business-column differences | 0 |
| Temporary candidate build | 28.309 s |
| Previous live refresh | 54.332 s |

The dependent objects were recreated and remained queryable. The measured candidate indicates
approximately 48% lower normalized-cash build time; a fresh complete baseline should be run after
the next normal application refresh to measure end-to-end impact.

| Stage | Object | Refresh | Read | Rows |
|---|---|---:|---:|---:|
| application | `app_v_normalized_daily_price_mv` | 90.839 s | 0.070 s | 34,364 |
| application | `app_v_normalized_cash_operations` | 54.332 s | 0.082 s | 19,846 |
| reconciliation | `recon_v_reconstructed_cash_daily_mv` | 53.191 s | 0.072 s | 6,089 |
| reconciliation | `recon_v_account_daily_reconciliation_mv` | 17.871 s | 0.068 s | 6,089 |
| reconciliation | `recon_v_trade_settlement` | 13.954 s | 0.108 s | 2,688 |
| application | `app_v_portfolio_daily_fx_rate_mv` | 5.644 s | 0.058 s | 2,001 |
| application | `app_v_portfolio_kpi_summary_mv` | 4.402 s | 0.094 s | 1 |
| application | `app_v_account_monthly` | 4.343 s | 0.066 s | 215 |
| application | `app_v_canonical_asset_daily_price_mv` | 6.030 s | 0.148 s | 86,703 |
| application | `app_v_portfolio_monthly` | 3.210 s | 0.065 s | 21 |
| reconciliation | `recon_v_account_daily_cashflow` | 6.492 s | 0.084 s | 6,089 |
| application | `app_v_symbol_performance` | 0.563 s | 0.091 s | 185 |
| reconciliation | `recon_v_reconstructed_position_daily_mv` | 23.172 s | 0.101 s | 42,456 |
| reconciliation | `recon_v_account_daily_cashflow_scope` | 0.617 s | 0.093 s | 6,089 |
| reconciliation | `recon_v_reconstructed_account_market_daily_mv` | 0.740 s | 0.070 s | 3,691 |
| reconciliation | `recon_v_account_monthly_profit` | 0.365 s | 0.070 s | 215 |
| reconciliation | `recon_v_account_statistics_vs_daily` | 0.208 s | 0.072 s | 11 |
| application | `app_v_current_asset_price_mv` | 0.339 s | 0.073 s | 208 |
| application | `app_v_portfolio_contribution_summary_mv` | 0.401 s | 0.069 s | 1 |
| application | `app_v_portfolio_currency_breakdown` | 0.378 s | 0.096 s | 7 |
| application | `app_v_portfolio_asset_allocation` | 0.176 s | 0.088 s | 18 |
| application | `app_v_canonical_asset_daily_price_ranked_mv` | 4.288 s | 0.110 s | 86,703 |

The application refresh total was approximately 175.801 s and reconciliation approximately
116.610 s, about 292.411 s combined. The dominant remaining work is normalized daily price,
normalized cash operations, reconstructed cash, reconstructed position daily, and account daily
reconciliation. The repeated FX aggregation bottleneck is no longer material: the three rebuilt
reporting MVs now refresh below one second each on this dataset. All 23 refreshed MVs remained
queryable with the expected row counts.

Validation update: after compiling the current reactor, `MaterializedViewRefreshContractIT` passed
6/6. The two stale compile errors were removed by updating the no-argument benchmark call to
`calculate(1L, null)`; the market-data error was stale compiled-class state and required no source
edit. Running the two broad unit classes afterward exposed unrelated existing behavioral failures
in the dirty worktree, so those tests are not used as a green gate for this DB-only change.

### 2026-09-03 normalized daily price investigation

`app_v_normalized_daily_price_mv` was profiled on live profile `.60` at the defining-query level.
The MV itself is cheap to read; the refresh spends its time finding the best historical price for
each position/date pair.

| Plan fact | Live result |
|---|---:|
| Raw position/date pairs before deduplication | 241,515 |
| Distinct asset/date pairs | 34,364 |
| Lateral price lookups | 34,364 |
| Ranked-price candidates visited per lookup | approximately 322 |
| Core defining-query execution | 58.118 s |
| Full MV refresh from baseline | 90.839 s |

The `position_dates` join and hash aggregate take about 6.7 s. The dominant node is the repeated
lateral lookup into `app_v_canonical_asset_daily_price_ranked_mv`: PostgreSQL uses the existing
index on `(asset_id, effective_observation_date, selection_priority, quality_score, price_date,
source, source_symbol)`, but still visits about 11 million candidate rows in total and performs a
top-N sort for every one of the 34,364 lookups. The sort includes the `price_origin` CASE tie-breaker
and the date/ranking predicates prevent the index from directly returning one final row.

The ranked-price MV currently has these relevant indexes:

- `ix_app_v_canonical_asset_daily_price_ranked_mv_order` on the ranking/order columns;
- `ux_app_v_canonical_asset_daily_price_ranked_mv_key` on `(asset_id, price_date)`.

A transaction-scoped expression index matching the `price_origin` tie-breaker was tested. It was
also a no-win: execution was 57.906 s with the CASE expression before `price_date`, and 58.310 s
with the exact production order including `price_date` before the CASE. Both plans retained the
top-N sort and approximately 322 candidates per loop. No permanent index was kept.

Conclusion: another index-only change is unlikely to materially improve this MV. The next safe
experiment is a temporary set-based candidate query: build the distinct `position_dates`, join
eligible ranked prices by asset/date, and select one winner per asset/date with `DISTINCT ON` (or a
window function) using the exact current ordering. Compare candidate rows and every business column
against the live MV before considering a migration. Preserve the existing lateral query if the
candidate changes tie-breaking, estimated-price interpolation, or null behavior.

The first naive `DISTINCT ON` plan was also checked. PostgreSQL estimates approximately 6.5 million
joined candidate rows followed by a global sort before deduplication. That is not an immediate
optimization: it replaces many small top-N sorts with one large spill-prone sort. The temporary
candidate build was therefore not promoted or applied. A better follow-up must reduce the candidate
set before sorting, for example by deriving the latest eligible observation date per asset/date and
then resolving the remaining ranking ties, while preserving the exact interpolation and tie-break
semantics.

The latest-observation-date variant was planned as well. It still performs the expensive
asset/date-to-ranked-price join first (estimated 6.5 million rows), then aggregates the latest date
for each pair and scans/sorts the ranked-price MV again to resolve ties. The planner estimates about
620k cost units for this shape and introduces a parallel scan plus merge/sort. The live execution
probe exceeded the local resource window before returning a result. It is therefore not a good
immediate candidate for production migration. The next investigation should seek a reusable
asset/date price map or a bounded incremental strategy, rather than recomputing the eligibility join
for every refresh.

### 2026-09-03 normalized daily price two-stage candidate

A second temporary shape kept the per-position/date lookup but split it into two indexed steps:

1. Find the latest eligible `effective_observation_date` for the asset/date.
2. Rank only rows at that observation date using the exact production tie-break order.

This avoided the original top-N sort over approximately 322 candidates per lookup. Live profile `.60`
results:

| Measure | Current lateral lookup | Two-stage candidate |
|---|---:|---:|
| Core query time | 58.118 s | 8.917 s |
| Candidate full-MV build | 90.839 s refresh baseline | 9.871 s |
| Output rows | 34,364 | 34,364 |
| Full projected-column differences | baseline | 0 |

The plan performed one indexed row fetch for the latest observation per pair and then ranked about
two rows per pair on average. The candidate matched all 20 projected columns with `EXCEPT ALL` in
both directions, including selected price, dates, source identity, interpolation flags, validation
status, and validation message. The candidate was transaction-scoped and rolled back; no permanent
index or view definition was changed.

This is now the strongest optimization candidate for `app_v_normalized_daily_price_mv`. Before
applying it live, capture a full refresh-order baseline and run the versioned migration through the
normal deployment path, then rerun the MV refresh contract and compare dependent application and
reconciliation outputs. Migration `V01.0118__two_stage_normalized_daily_price_lookup.sql` now
contains this change and the FastDatabase snapshot has been aligned with it. It has not been run on
live profile `.60` in this step.

### 2026-09-03 live application and reporting-function timing

The duplicate Flyway version blocker was resolved by assigning unique append-only versions:
`V01.0117__stable_import_source_row_identity.sql` and
`V01.0118__two_stage_normalized_daily_price_lookup.sql`. Flyway validation then succeeded on live
profile `.60`; the database was at `01.0108`, so pending migrations `01.0109` through `01.0118` were
applied out of order relative to already-present `01.012`, without rewriting checksums.

The application function and reconciliation reporting function were rerun with `jit=off`. The
reconciliation reporting function does not include the three reconstruction MVs; the complete
object-by-object baseline below is the authoritative all-MV comparison.

| Scope | Previous baseline | Current live baseline | Change |
|---|---:|---:|---:|
| Application function | n/a | 61.102 s | n/a |
| Reconciliation reporting function only | n/a | 23.642 s | n/a |
| Function calls combined | n/a | 84.744 s | n/a |

All 23 expected MVs remained queryable after refresh, with expected row counts. The normalized daily
price MV returned 34,364 rows and a full count read took 21 ms. The migration and dependent-object
rebuild completed successfully. The previous 116.610 s reconciliation figure included reconstruction
MVs and must not be compared with the 23.642 s reporting-function-only call.

### 2026-09-03 object-by-object comparison with yesterday's populated baseline

The complete ordered refresh was rerun per object on live `.60` with `jit=off`; regular views were
measured with `SELECT count(*)`. The table below compares the first populated baseline recorded on
2026-09-02 with the current detailed run. Small increases on cheap objects are normal measurement
noise; the large reporting-MV reductions are structural.

#### Materialized views

| MV | Yesterday | Current | Change |
|---|---:|---:|---:|
| `recon_v_reconstructed_cash_daily_mv` | 52.9 s | 52.824 s | -0.1% |
| `recon_v_reconstructed_position_daily_mv` | 20.1 s | 25.013 s | +24.4% |
| `recon_v_account_daily_reconciliation_mv` | 18.8 s | 18.351 s | -2.4% |
| `recon_v_trade_settlement` | 17.0 s | 15.018 s | -11.7% |
| `app_v_normalized_cash_operations` | 54.3 s | 32.365 s | -40.4% |
| `app_v_normalized_daily_price_mv` | 77.6 s | 12.413 s | -84.0% |
| `app_v_account_monthly` | 3.8 s | 3.611 s | -5.0% |
| `app_v_portfolio_kpi_summary_mv` | 3.7 s | 3.848 s | +4.0% |
| `app_v_portfolio_monthly` | 3.5 s | 3.321 s | -5.1% |
| `app_v_portfolio_daily_fx_rate_mv` | 5.6 s | 4.997 s | -10.8% |
| `app_v_canonical_asset_daily_price_mv` | 4.3 s | 6.589 s | +53.2% |
| `app_v_canonical_asset_daily_price_ranked_mv` | 6.1 s | 5.716 s | -6.3% |
| `recon_v_account_daily_cashflow` | 5.8 s | 5.639 s | -2.8% |
| `app_v_account_statistics` | 106.1 s | 0.768 s | -99.3% |
| `app_v_symbol_performance` | 89.6 s | 0.517 s | -99.4% |
| `app_v_portfolio_currency_breakdown` | 107.6 s | 0.310 s | -99.7% |
| `recon_v_account_daily_cashflow_scope` | 0.5 s | 0.812 s | +62.4% |
| `recon_v_reconstructed_account_market_daily_mv` | 0.3 s | 0.561 s | +87.0% |
| `recon_v_account_monthly_profit` | 0.5 s | 0.411 s | -17.8% |
| `recon_v_account_statistics_vs_daily` | 0.2 s | 0.154 s | -23.0% |
| `app_v_portfolio_contribution_summary_mv` | 1.0 s | 0.396 s | -60.4% |
| `app_v_portfolio_asset_allocation` | 0.2 s | 0.083 s | -58.5% |
| `app_v_current_asset_price_mv` | 0.2 s | 0.187 s | -6.5% |

Detailed all-object totals: application MVs **175.801 s -> 75.127 s** (-57.3%);
reconciliation MVs **116.610 s -> 118.786 s** (+1.9%); combined **292.411 s -> 193.913 s**
(-33.7%). The earlier 84.744 s function-call figure excluded reconstruction MVs.

#### Regular views, read time

| View | Yesterday | Current | Change |
|---|---:|---:|---:|
| `recon_v_portfolio_service_fallback` | 0.010 s | 0.012 s | +20.0% |
| `recon_v_non_usd_closed_trade` | 15.800 s | 0.349 s | -97.8% |
| `recon_v_reconciliation_position_issues` | 3.200 s | 2.603 s | -18.7% |
| `recon_v_asset_price_quality_issues` | 4.600 s | 5.405 s | +17.5% |
| `app_v_portfolio_daily` | 2.900 s | 3.109 s | +7.2% |
| `app_v_portfolio_performance_daily` | 3.700 s | 3.065 s | -17.2% |
| `recon_v_position_valuation_validation` | 2.900 s | 3.253 s | +12.2% |
| `recon_v_validation_issue` | 3.300 s | 3.174 s | -3.8% |
| `recon_v_realized_result` | 1.900 s | 1.946 s | +2.4% |
| `app_v_normalized_daily_price` | 0.020 s | 0.023 s | +15.0% |

The current run confirms the main optimization result: the normalized daily-price refresh fell from
77.6 s to 12.4 s, while the shared-FX reporting MVs remain sub-second. The remaining large MV
bottlenecks are reconstructed cash, normalized cash, and reconstructed position daily. Regular-view
reads are generally stable; `recon_v_non_usd_closed_trade` is now fast after its rewrite.
