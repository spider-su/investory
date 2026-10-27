# Reporting pipeline

This document defines the stable data lineage from imported ledger rows to reporting surfaces.
Exact view definitions and column lists live in Flyway migrations.

## Data lineage

```text
broker imports
  -> immutable import_source_files / import_source_rows
  -> positions / cash_operations / accounts / assets / exchange_rates
  -> normalized cash ledger and position valuation
  -> account_daily
  -> portfolio/account reporting views and materialized views
  -> PortfolioMetricsService / BenchmarkService
  -> InvestmentDashboardFacade -> Thymeleaf dashboard
  -> compatibility APIs / exports
```

## Raw and normalized ledger

`positions` is the unified position table. Open and closed positions are different states of the same
underlying position model.

`cash_operations` stores imported cash movements. Reporting classification must distinguish external
flows from internal transfers, currency conversion, investment income, taxes, fees, and other ledger
movements.

`normalized_cash_operations` is the classified reporting ledger over cash operations. It is the
preferred place for database reporting semantics; direct `cash_operations` reads are appropriate for
ledger-detail use cases that need the original operation granularity.

`import_source_files` stores one immutable uploaded broker artifact per provider/checksum. Reprocess
attempts reuse that payload. `import_source_rows` stores immutable parsed evidence for each broker row;
new broker-created `cash_operations` and `positions` carry both `import_history_id` and
`import_source_row_id`. Legacy/manual rows may remain nullable. Use `ImportOriginLookupService` for
deliberate diagnostic origin inspection; raw evidence is not exposed by dashboard endpoints.

Asset and currency semantics are defined by:

- `docs/domain/asset-identity-and-money.md`
- `docs/domain/fx-normalization.md`

## Daily projection boundary

`account_daily` is the persisted daily read-model boundary. Each row represents one account and one
date and combines end-of-day state with that day's flows and valuation.

It is live projection data, not disposable legacy history.

When imported dates, ledger classification, market prices, or FX data change, rebuild the projection
instead of duplicating or patching dashboard calculations.

## Higher reporting layers

Portfolio and monthly summaries above `account_daily` are database-derived views/materialized views.
Important consumers include portfolio KPI, asset allocation, currency breakdown, performance, and
reconciliation surfaces.

These layers are snapshots/derived projections, not independent sources of financial truth. They must
be refreshed after inputs that affect valuation or classification change.

Performance metric ownership is split by boundary. Normalized accounting owns flow classification
and investment-result semantics. `account_daily` owns persisted daily equity, flows, income, fees,
taxes, and realized-result facts. SQL reporting owns period aggregation, FX normalization, and
monthly return fields. Java application composition exposes those facts through
`investment.reporting.PerformanceResult`; it does not recompute historical accounting formulas.
Unrealized performance is not populated in `PerformanceResult` until a canonical period-level source
is available. Existing `Performance` and dashboard view models remain compatibility/presentation
models and may retain `double` fields at their legacy chart/UI boundary. The public
`investment.api.reporting.InvestmentPerformanceApi` exposes canonical reporting numbers as
`BigDecimal`; conversion to chart/display primitives happens only in the adapter boundary.

### Portfolio dashboard metric source matrix

| Metric | Authoritative source | Currency and period | Missing-data behavior | Production fallback |
| --- | --- | --- | --- | --- |
| Portfolio equity, cash, deposits, withdrawals, net deposits, realized/unrealized result, headline ROI | `app_v_portfolio_kpi_summary_mv` and `app_v_portfolio_contribution_summary_mv` via `PortfolioKpiSummaryRepository` | Portfolio base currency; since inception/current snapshot; `cash_only` accounts excluded | Missing row or null equity sets `Portfolio.valuationComplete=false`; zero-initialized display fields are unavailable placeholders, not valid zero results | None. Java ledger reconstruction, currency-breakdown totals, account-statistics totals, and open-position P/L reconstruction are not used |
| Headline interest and dividend withholding tax enrichment | `app_v_account_statistics` plus normalized cash operations aggregated by `CashFlowAggregator` | Portfolio base currency; cumulative through snapshot | Existing KPI remains authoritative; optional enrichment retains initialized zero if contributing projections/rows are absent | Supplement only; does not replace KPI headline accounting |
| Per-account balance, cash, profit, ROI | `app_v_account_statistics` for current balance/cash; `app_v_normalized_cash_operation_flows` grouped in SQL by account/day/category then summed in Java for account funding; `app_v_account_monthly` for selected-period profit/return | Portfolio base amounts plus account-native display amounts; current snapshot and selected period | No statistics means no account cards; absent funding rows use the account-statistics net-deposit fields | Legacy XTB subaccount funding adjustment remains when normalized account-flow base amounts are unavailable; older `amount_in_account_currency` remains a compatibility source for local account-funding display. Neither changes portfolio headline totals |
| Selected-period and monthly performance | Canonical SQL reporting over `account_daily` / `app_v_portfolio_performance_daily` and monthly portfolio rows, composed by `PortfolioPerformanceQueryService` | Portfolio base currency; requested period | Missing/invalid valuation boundaries produce unavailable reporting metrics per the reporting contract | No raw-ledger dashboard reconstruction |
| Capital-gains tax estimate | `app_v_portfolio_tax_year_realized` when present, otherwise `TaxCalculator` over scoped closed positions | Portfolio base currency; current tax year with loss carry-forward | Empty tax projection invokes the existing explicit estimate calculation | Retained tax estimate path; separate from headline earnings |

The previous Java headline reconstruction from closed/open positions and raw cash operations,
followed by portfolio-currency, account-statistics, or open-position substitutions when KPI data was
missing, is retired from normal reads. Those algorithms are not interchangeable with the canonical
normalized reporting boundary and must not be reintroduced as an implicit missing-projection repair.
XTB account-level funding remains a separate unresolved compatibility behavior; changing it requires
independent characterization and financial-contract review, not a headline cleanup.

### UI monetary amount inventory (source-lineage pass)

This inventory covers user-visible currency amounts and security prices in the server-rendered UI,
including chart/tooltip values and modal or form fields that repeat persisted amounts. Counts,
quantities, percentages, rates, dates, ages, and non-financial numeric settings are outside scope.
This is a source-code lineage inventory; it does not verify a running database's current values or
prove which source each deployed page reads at runtime. That runtime comparison is a separate pass.

“Existing read model” means a view or materialized view already exposes the amount or a close
projection of it. It does not imply the page currently consumes that object. “No consolidated read
model” means the UI value is assembled from source tables or module APIs and/or calculated in Java.

| UI surface | Amounts shown | Existing view/materialized-view coverage | Source-level path and notes |
| --- | --- | --- | --- |
| Investment dashboard header, account popover, and period cards | Portfolio value, cash, invested/net external contributions, deposits, withdrawals, profit, per-account balances/cash/funding, selected-period profit and income | Yes: `app_v_portfolio_kpi_summary_mv`, `app_v_portfolio_contribution_summary_mv`, `app_v_account_statistics`, `app_v_portfolio_performance_daily`, and monthly reporting views | Portfolio KPI reads the KPI summary; account rows read account statistics plus normalized account flows. Period metrics use canonical performance reporting. Some page and section values are composed in Java. Cash-only exclusion for portfolio-wide totals is defined in `V01.012`. |
| Dashboard portfolio structure, positions, instrument details, and price modal | Allocation/currency totals, current position value, cost, unrealized/realized profit, dividends and withholding tax, current/average quote, projected value, historical prices | Yes: `app_v_portfolio_asset_allocation`, `app_v_portfolio_currency_breakdown`, `app_v_open_position_values`, `app_v_current_asset_price`, `app_v_symbol_performance` | `PortfolioMetricsService` reads the `app_v_portfolio_asset_allocation` MV and maps its rows to dashboard position DTOs; Java adds display fields such as position share and price-source label. That MV is built from `app_v_open_position_values`. Category and account-currency buckets are grouped in Java from per-asset/account rows. `AssetDetailService` reads position and `cash_operations` records and aggregates holdings, realized profit, and dividend totals in Java, with symbol-performance and price-history data available separately. The manual-price modal displays persisted price facts and submits a new observation; it is an input flow, not a report. |
| Dashboard realized result, dividends, and tax details | Realized profit, dividend/income amounts, withholding/tax amounts, current-year capital-gains tax estimate | Partial: `app_v_portfolio_tax_year_realized`, `app_v_account_monthly`, `app_v_portfolio_monthly`, and `app_v_symbol_performance` | Realized/tax facts have SQL reporting inputs; the empty tax-year projection explicitly falls back to `TaxCalculator` over closed positions. Dividend/tax detail and payer summaries include Java composition. |
| Investment Profile | Net worth, brokerage value, Long-Term value, asset allocation, liquid/illiquid capital, reserve, annual income, investment income base/forecast, YTD result, planned Long-Term income | Partial: underlying Investment KPI/allocation/monthly views and the simple `app_v_long_term_assets` inventory view exist; there is no unified Profile view/MV | `ProfileQueryService` reads Investment and Long-Term APIs, performs display-currency conversion and combines the results with Profile calculators. `InvestmentIncomeSummaryService` starts from monthly portfolio reporting and calculates weighted income base/projections in Java. Long-Term's existing union view is not the read path used by `LongTermAssetReadService`. |
| Long-Term asset inventory and real-estate detail | Asset current values and totals, group allocation, gross/net rental income, expenses/tax, yield, property total return, contract payment/tax terms | Partial: `app_v_long_term_assets` is a simple union view over `bond`, `real_estate`, `cash_reserve`, and `personal_asset`; `recon_v_long_term_assets` is diagnostic | The UI currently reads type-specific repositories and rental-contract tables. `LongTermAssetReadService` converts currencies, groups and sums values, and `LongTermAssetEconomics` calculates annual/monthly economics and return in Java. No consolidated economics view/MV is defined. Create/edit forms display stored amount inputs from those same records. |
| Retirement simulation plan, annual costs, and yearly timeline | Starting/current/expected bucket balances, income and spending, monthly cost groups, annual extras, funding gap/unfunded amount, withdrawals, contributions, source amounts, year-over-year changes | No reporting view/MV for generated projections. Persisted inputs live in plan, planning-year, event, and annual-cost tables | Retirement combines saved inputs with Investment/Profile/Long-Term facts, then produces deterministic projections in Java. Timeline rows and displayed totals are generated results, not stored reporting facts. Forms show persisted plan/cost input values. |
| Financial Landscape and live-year review | Current and expected wealth/buckets, annual and monthly income/costs, funding surplus/gap, expense groups | No unified view/MV | `FinancialLandscapePageAssembler` combines Retirement projection/timeline/cost APIs with Profile facts. Amounts are assembled in the page model from those module outputs. |
| Retirement analysis and sandbox | Scenario minimum/final liquid assets and net worth, spending limits/headroom, yearly income/spending/funding chart amounts, override and scenario amounts | No reporting view/MV for analysis/sandbox outputs | Analysis prepares scenarios/sensitivities and runs the deterministic retirement model in Java. Saved inputs remain in the retirement tables; generated output is calculated per request. |
| Dashboard reconciliation | Reported/current/previous amounts, valuation and cash-flow deltas, trade-settlement amounts, review evidence amounts | Yes, across diagnostic views/materialized views such as `recon_v_account_daily_reconciliation_mv`, `recon_v_account_daily_cashflow`, `recon_v_account_monthly_profit`, `recon_v_account_statistics_vs_daily`, `recon_v_trade_settlement`, and related `recon_v_*` objects | Reconciliation reports project SQL diagnostic values through the reporting API. The page formats these values and combines them with persisted review records; no single reconciliation amount view covers the whole page. |
| Market Radar detail and strategist outlook | Latest security close, forecast target/median target level, and forecast error points | No consolidated UI read-model view/MV; radar snapshots and strategist forecast records are stored separately | Historical bars come through the market-data port; `MarketSignalCalculator` derives close-based indicators in Java and the radar snapshot store persists results. The detail page displays the stored snapshot close; `StrategistOutlookService` calculates median and forecast summaries from forecast records. These are quote/index levels, not portfolio currency totals. |

#### First-pass candidates for the DB-versus-Java review

- Dashboard open-position values: `app_v_portfolio_asset_allocation` already feeds the UI and is built from `app_v_open_position_values`. Compare the underlying view, materialized aggregation, and Java-derived display fields (especially total/share) for consistency; this is not a raw-ledger Java valuation path.
- Profile totals/allocation/income: there is no single read model today. Review `ProfileQueryService` composition and its calculator outputs against existing Investment and Long-Term views before proposing a new projection.
- Long-Term totals and annual economics: `app_v_long_term_assets` only unifies base inventory fields. It does not currently expose the Java-calculated converted groups, rental economics, taxes, or yields.
- Retirement projections and analysis: no read-only SQL projection exists. The amounts are outputs of the deterministic Java model over persisted plan inputs and source-domain snapshots; materializing them would introduce a distinct snapshot lifecycle and is not implied by this inventory.
- Dashboard reconciliation: SQL read models already exist per diagnostic family; verify page/API coverage against each amount family rather than creating a replacement aggregate view.

Input forms are included as displays of persisted values, but they are not treated as reporting read models. A form field's presence in a database table does not make it a calculated or reusable reporting projection.

Return metrics use the canonical normalized flow semantics. `EXTERNAL_DEPOSIT` and
`EXTERNAL_WITHDRAWAL` are investor cash flows; `INTERNAL_TRANSFER_IN/OUT`, security transfers,
and `FX_CONVERSION` do not become portfolio-level investor flows. Dividends, interest, fees, and
taxes remain investment-result components. TWR uses daily portfolio valuation boundaries and treats
same-day external flow as a beginning-of-day boundary adjustment. XIRR uses contributions as
negative investor cash flows, withdrawals as positive cash flows, and includes the opening value as
an initial negative flow and ending value as the terminal positive flow. Missing/invalid boundaries
produce an unavailable metric, not zero.

Income Base is the reporting input for projected market income. Its meaning and month-weighted
external-flow treatment are owned by `InvestmentIncomeSummaryService` and the tested
`InvestmentIncomeCalculator` contract. UI and AI tests must reference that contract and the
canonical HappyInvestor source facts, including applicable external deposits and withdrawals;
they must not replace it with a January-1-only balance or infer it from a label.

Dashboard TWR and XIRR read base-currency, non-cash-only daily boundaries from
`app_v_portfolio_performance_daily`; application code must not rebuild them by summing raw
account-currency `account_daily` rows. Selected-period and fixed KPI-start returns therefore share
the same reporting boundary and differ only by their requested dates. At portfolio inception, the
first monthly row can use its closing equity as the opening valuation when there is no earlier monthly
observation. That same first day's external flows are already reflected in this fallback valuation,
so the first daily boundary is excluded from the return calculation; counting those flows again would
distort TWR and XIRR. When a prior monthly observation exists, the ordinary daily flow-adjustment path
applies.

### Reconciliation materialization boundary

Reconciliation keeps expensive independent reconstruction separate from cheap reporting joins:

```text
positions + historical prices + FX
  -> recon_v_reconstructed_position_daily_mv
  -> recon_v_reconstructed_account_market_daily_mv
normalized cash ledger + FX
  -> recon_v_reconstructed_cash_daily_mv
account_daily + reconstructed facts + realized-result view
  -> recon_v_account_daily_reconciliation_mv
  -> recon_v_account_daily (compatibility view)
```

`app_v_reconstructed_position_daily`, `app_v_reconstructed_cash_daily`, and
`recon_v_account_daily` remain public compatibility views. Reconciliation diagnostics are
rewired to the corresponding `_mv` facts. The realized-result reconstruction remains a view: it is
one account/date aggregation over closed positions and cash rows, without the position-by-day
history expansion that justifies the other materializations.

The C1 application check reads `recon_v_account_daily_cashflow_full_precision`. The existing
`recon_v_account_daily_cashflow` materialized view remains a rounded diagnostic
surface; display rounding never determines C1 status.

Application-facing `app_v_*` objects expose presentation-ready reporting projections and may round
monetary values at that boundary. Reconciliation-facing `recon_v_*` objects preserve raw/full
precision wherever values participate in evidence or status decisions. The `_mv` suffix identifies
an internal materialized fact when a same-purpose application or reconciliation view already owns
the unsuffixed name; it does not change the precision contract.

The complete materialized-view refresh order is:

1. Application reporting inputs: `app_v_current_asset_price_mv` and
   `app_v_portfolio_daily_fx_rate_mv`. These bounded, reusable inputs must refresh before
   current-position consumers and the remaining application MVs.
2. Application production views: `app_v_normalized_cash_operations`, `app_v_account_monthly`,
   `app_v_portfolio_monthly`, `app_v_account_statistics`,
   `app_v_portfolio_contribution_summary_mv`, `app_v_portfolio_currency_breakdown`,
   `app_v_portfolio_asset_allocation`, `app_v_symbol_performance`, and
   `app_v_portfolio_kpi_summary_mv`.
3. Reconciliation reconstruction facts: `recon_v_reconstructed_position_daily_mv`,
   `recon_v_reconstructed_account_market_daily_mv`, `recon_v_reconstructed_cash_daily_mv`, and
   `recon_v_account_daily_reconciliation_mv`.
4. Reconciliation reporting facts: `recon_v_account_monthly_profit`,
   `recon_v_account_statistics_vs_daily`, `recon_v_account_daily_cashflow`,
   `recon_v_account_daily_cashflow_scope`, and `recon_v_trade_settlement`.

`PortfolioProjectionRefreshService` owns production refresh orchestration. Each application refresh
scope runs its selected dependency-ordered bundle in one `REQUIRES_NEW` transaction under the shared
transaction-scoped advisory lock. PostgreSQL readers retain MVCC visibility of the previous
committed materialized-view generation while refreshes run; a successful commit publishes the
bundle, while a failure rolls back the transaction and leaves the prior committed generation
visible. PostgreSQL releases the advisory lock on either commit or rollback. The application uses
`REFRESH MATERIALIZED VIEW CONCURRENTLY` (except the canonical daily-price MV, which is refreshed
non-concurrently); transaction rollback, retry, and reader behavior are covered by PostgreSQL
contract tests. A failed refresh is logged with plan, view, and elapsed time and propagated to the
caller; success is not reported. The caller's cache invalidation remains in `finally` where
applicable, removing potentially stale cached calculations after either outcome.

Reconciliation has a distinct policy. `refresh_reconstructed_position_daily()` runs in its own
transaction and refreshes source price MVs plus the first reconstruction MV; reconciliation
reporting MVs then run in a second transaction in explicit dependency order. Each stage is atomic,
but the complete reconstruction-plus-reporting sequence is not one published generation. A failure
propagates, keeps the failed transaction's prior MV state, and does not prevent a later retry.
Ordinary dashboard refresh plans do not invoke reconstruction. The SQL refresh functions remain
migration/compatibility infrastructure and are not used by production application orchestration.
View-only aliases do not require refreshes.

The reconstruction stage functions rebuild the facts in graph order. Post-import application
orchestration invokes its stages separately, so a slow stage is measurable and does not hold one
large application transaction. The transaction policy above applies to the production Java plan;
compatibility SQL function transaction behavior is determined by the caller's transaction. The
system audit runs asynchronously after import finalization has committed; an audit failure cannot
roll back canonical imported data.

For exact current view names and definitions, inspect `app/src/main/resources/sql/migration`.

## SQL migration boundaries

`V01.000__schema.sql` defines the core schema: reference tables, enums, raw ledger tables
(`accounts`, `positions`, `cash_operations`), import provenance, price/FX tables, and base functions.

`V01.001__functions.sql` defines the shared database functions, including FX resolution and
status handling. `V01.002__triggers.sql` contains table-integrity triggers only; FX refresh
and daily-history activation are application-driven through the repository. `V01.003__initial_data.sql`
seeds the anonymized sample portfolio/configuration and public/reference market data. `V01.004__asset_price_history_import.sql`
adds baseline asset mappings and price observations.

`V01.005__portfolio_views.sql` contains the production calculation layer that consumes the
FX resolver from `V01.001__functions.sql`, the canonical FX usable-status helper
`fx_status_usable()`, production
calculation views, and the production materialized views `app_v_account_monthly`,
`app_v_portfolio_monthly`, `app_v_account_statistics`, `app_v_portfolio_kpi_summary_mv`,
`app_v_portfolio_currency_breakdown`, `app_v_portfolio_asset_allocation`, `app_v_symbol_performance`,
and `app_v_portfolio_contribution_summary_mv`. `refresh_app_views()` refreshes those objects in explicit order.

The current-price and portfolio/date/currency FX materialized inputs are defined in
`V01.005__portfolio_views.sql`. `app_v_normalized_daily_price` remains the full-history
view for historical valuation; current-position reporting uses `app_v_current_asset_price_mv` and
`app_v_portfolio_daily_fx_rate_mv` instead.

`V01.006__reconciliation_views.sql` contains read-only reconstruction and diagnostic objects and the
public refresh functions. The stage functions refresh reconciliation materializations on
demand; they are not production refresh dependencies. `V01.007__persisted_system_audit.sql` remains the
persisted audit subsystem and consumes reconciliation review data.

Import provenance tables, canonical-row links, immutability triggers, and diagnostics are part of the
current squashed core/reconciliation baseline. `V01.008__long_term_assets.sql` adds manual Long-Term
asset, subtype, lifecycle, valuation/rate, rental-contract, planning-year, and simulation-plan
persistence. The deterministic bucket model stores only its active return assumptions in the baseline
schema. The canonical full-precision C1 evidence view is part
of `V01.006__reconciliation_views.sql`, and rental-contract subtype enforcement is part of
`V01.008__long_term_assets.sql`.

Integration adapters are under `com.smartbox.investory.integrations`; Investment-owned provider
contracts remain under `investment.port`. The configured FX adapter is
`NbpFxDataPlugin` as the FX adapter. `PluginRegistry` provides typed Spring discovery. IBKR and XTB parser
beans expose the broker-import port; Yahoo Finance owns external ticker mapping and request-key
overrides; Yahoo export is exposed through the export port. Persisted FX and market jobs are polled
by `IntegrationJobScheduler`, with PostgreSQL advisory locks preventing overlapping runs. Runtime
application configuration remains the compatibility fallback. The application-layer
`IntegrationSettingsFacade` owns descriptor validation, encrypted secret lifecycle, job validation,
and read-only settings DTOs. An enabled persisted integration instance takes precedence over the
legacy environment configuration; disabled or absent instances do not override it. Secrets are
never returned to callers, only their configured state is exposed.

Application-facing aliases use the `app_v_` prefix. Reconciliation and diagnostic aliases use
`recon_v_`; materialized facts use the same layer prefix and an `_mv` suffix where needed. No new
bare `v_`, `mv_`, or `reporting_` view names should be introduced. Neither alias family is a financial source of truth. `accounts.id` is an internal generated
identity. Broker identifiers are stored in `accounts.external_account_id` and are unique by provider.
Raw account, position, and cash-ledger history is protected from account or portfolio deletion; derived
projection rows may be deleted and rebuilt.

This naming scheme is frozen with the baseline. Constraint names describe the constrained behavior and
do not carry migration-version suffixes; historical migration names are not renamed after deployment.

## FX usable-status contract

Authoritative SQL monetary conversion uses a single helper, `investory.fx_status_usable(status)`, defined in
`V01.001__functions.sql`. `OK`, `ESTIMATED`, `SAME_CURRENCY`, and `CARRY_FORWARD` are usable;
`MISSING_RATE` and `MISSING_CURRENCY` are not. Production views and materialized views call the helper
directly. `resolve_fx_rate()` selects the latest neutral valuation observation on or before the
valuation date (direct pair before inverse on a date tie); it preserves source date and age for a
`CARRY_FORWARD` result and imposes no age cutoff. The daily-history boundary controls refresh coverage,
not resolver eligibility. Execution observations have separate transaction-only purpose rules; see
[`../domain/fx-normalization.md`](../domain/fx-normalization.md).

The Java `CurrencyRateService` is the application conversion boundary. It reads resolved valuation
matrices through `CurrencyRateRepository`, caches complete matrices by date, performs `BigDecimal`
conversion, accepts the same usable statuses as SQL, and fails closed for missing or otherwise
unusable rates. FX refresh no longer depends on a database
FX trigger: `CurrencyRateUpdaterService` persists observations explicitly and advances the daily-history
boundary only after `setDailyHistoryStartIfSupported(...)` confirms neutral coverage.

## Current versus historical prices

Current open-position valuation uses the canonical current asset quote maintained by market/manual
price services. Historical valuation uses price history according to the current projection logic.

Do not create a second independent notion of "current price" in a dashboard query, export, or
compatibility adapter. Reporting surfaces should converge on the same canonical valuation inputs.

## Reporting consistency

The following surfaces should agree within their defined rounding and scope:

- latest account projection;
- portfolio KPI/header totals;
- portfolio asset allocation/open-position totals;
- dashboard account and position views;
- compatibility/export surfaces that claim the same metric.

When they disagree, trace the value downward through the reporting pipeline instead of compensating at
the UI layer.

See `docs/quality/reconciliation.md` for checkpoint and economic-truth validation.

The dashboard consumes these existing reporting services through `InvestmentDashboardFacade`. The facade maps
their results into UI-specific view models; it is not a second reporting source of truth and does not
change the `account_daily` boundary.

## Migration history contract

The empty-database chain is `V01.000` through `V01.009`. Final portfolio-scoped Yahoo
export state, per-attempt import evidence links, and valuation FX indexes live in the core
schema. The final optimized FX resolver lives in `V01.001`; inception performance and
complete-valuation presentation live in `V01.005`. Existing databases receive reviewed
operational alignment before their obsolete upgrade history is removed. The generated
snapshot follows this create/insert chain. Historical repair scripts are not replayed on
new onboarding.

## Complete valuation publication

Current-price refresh rebuilds the portfolio/date FX input before account statistics. Each
application MV refresh bundle holds the shared advisory lock and commits in one transaction;
readers cannot observe an intermediate bundle. Reconstruction and reconciliation reporting are
separate atomic stages as described above, not one cross-stage transaction. Portfolio KPI
presentation masks cash, market value, equity, unrealized P/L and ROI when valuation is incomplete. The dashboard shows an
unavailable balance, incomplete calculations are not cached, and operational notifications
are deferred without changing drawdown state. Full projection refresh invalidates calculation
caches after the reporting bundle completes (also on failure to remove stale entries).

The scheduled `refresh-prices` maintenance action refreshes market prices and benchmark closes,
then runs `StatisticsRefreshService.refreshAll()` before returning success. This rebuilds
`account_daily` through the business date, publishes the full application reporting bundle and
invalidates calculation caches. A projection or reporting refresh failure fails the job and must
not be reported as a successful price refresh. The current-price MV refresh alone does not advance
daily snapshots. Existing gaps can be repaired through the `rebuild-monthly` maintenance action,
which rebuilds daily facts as well as their monthly reporting views.

Persisted Telegram daily notifications default to 09:00 Europe/Warsaw on weekdays. Automatic
execution is limited to fifteen minutes following the scheduled time; missed runs do not catch
up overnight on startup. Explicit manual execution remains available.
