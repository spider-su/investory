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
`services/portfolio/read/PerformanceResult`; it does not recompute historical accounting formulas.
Unrealized performance is not populated in `PerformanceResult` until a canonical period-level source
is available. Existing `Performance` and dashboard view models remain compatibility/presentation
models and may retain `double` fields at their legacy chart/UI boundary. The public
`investment.api.reporting.InvestmentPerformanceApi` exposes canonical reporting numbers as
`BigDecimal`; conversion to chart/display primitives happens only in the adapter boundary.

Return metrics use the canonical normalized flow semantics. `EXTERNAL_DEPOSIT` and
`EXTERNAL_WITHDRAWAL` are investor cash flows; `INTERNAL_TRANSFER_IN/OUT`, security transfers,
and `FX_CONVERSION` do not become portfolio-level investor flows. Dividends, interest, fees, and
taxes remain investment-result components. TWR uses daily portfolio valuation boundaries and treats
same-day external flow as a beginning-of-day boundary adjustment. XIRR uses contributions as
negative investor cash flows, withdrawals as positive cash flows, and includes the opening value as
an initial negative flow and ending value as the terminal positive flow. Missing/invalid boundaries
produce an unavailable metric, not zero.

Dashboard TWR and XIRR read base-currency, non-cash-only daily boundaries from
`app_v_portfolio_performance_daily`; application code must not rebuild them by summing raw
account-currency `account_daily` rows. Selected-period and fixed KPI-start returns therefore share
the same reporting boundary and differ only by their requested dates.

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

`PortfolioProjectionRefreshService` owns production refresh orchestration. It refreshes each
materialized view in dependency order, with one transaction per view, and selects the application
plan for broker imports, FX updates, current market prices, historical rebuilds, or full repair.
Reconciliation is a separate Java plan; stage 3 must follow stage 2 when it consumes reconstructed
facts. The SQL refresh functions remain migration/compatibility infrastructure and are not used by
production application orchestration. View-only aliases do not require refreshes.

The reconstruction stage functions rebuild the facts in graph order. Post-import application
orchestration invokes its stages separately, so a slow stage is measurable and does not hold one
large application transaction. The system audit runs asynchronously after import finalization has
committed; an audit failure cannot roll back canonical imported data.

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
`ExchangeRateHostFxDataPlugin`; `PluginRegistry` provides typed Spring discovery. IBKR and XTB parser
beans expose the broker-import port; TwelveData owns external ticker mapping and request-key
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
`V01.001__functions.sql`. It treats `OK`, `ESTIMATED`, and `SAME_CURRENCY` as usable and
`STALE`, `MISSING_RATE`, `MISSING_CURRENCY` as not usable. The usable-status list is not duplicated or
patched after the fact; production views and materialized views call the helper directly. Historical
interpolation in `resolve_fx_rate()` is bracketed deterministically by the nearest observation strictly
before and strictly after the valuation date within one source/method series, and only before the
`fx_configuration.daily_history_start` boundary.

The Java `CurrencyRateService` is the application conversion boundary. It reads resolved valuation
matrices through `CurrencyRateRepository`, caches complete matrices by date, performs `BigDecimal`
conversion, and rejects `STALE` and `MISSING_RATE` results. FX refresh no longer depends on a database
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

The current empty-database chain is the complete ordered set under
`app/src/main/resources/sql/migration`, from `V01.000` through `V01.008`. Later table, function, view,
and materialized-view changes are incorporated into those baseline migrations. The generated
fast-test snapshot is derived from this chain but never replaces it. Existing databases were brought
to the same final schema before this history cleanup.
