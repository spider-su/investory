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
  -> PortfolioService / BenchmarkService
  -> DashboardFacade -> Thymeleaf dashboard
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
models and may retain `double` fields.

Return metrics use the canonical normalized flow semantics. `EXTERNAL_DEPOSIT` and
`EXTERNAL_WITHDRAWAL` are investor cash flows; `INTERNAL_TRANSFER_IN/OUT`, security transfers,
and `FX_CONVERSION` do not become portfolio-level investor flows. Dividends, interest, fees, and
taxes remain investment-result components. TWR uses daily portfolio valuation boundaries and treats
same-day external flow as a beginning-of-day boundary adjustment. XIRR uses contributions as
negative investor cash flows, withdrawals as positive cash flows, and includes the opening value as
an initial negative flow and ending value as the terminal positive flow. Missing/invalid boundaries
produce an unavailable metric, not zero.

### Reconciliation materialization boundary

Reconciliation keeps expensive independent reconstruction separate from cheap reporting joins:

```text
positions + historical prices + FX
  -> mv_reconstructed_position_daily
  -> mv_reconstructed_account_market_daily
normalized cash ledger + FX
  -> mv_reconstructed_cash_daily
account_daily + reconstructed facts + realized-result view
  -> mv_account_daily_reconciliation
  -> v_account_daily_reconciliation (compatibility view)
```

`v_reconstructed_position_daily`, `v_reconstructed_cash_daily`, and
`v_account_daily_reconciliation` remain public compatibility views. Reconciliation diagnostics are
rewired to the corresponding `mv_*` facts. The realized-result reconstruction remains a view: it is
one account/date aggregation over closed positions and cash rows, without the position-by-day
history expansion that justifies the other materializations.

`refresh_reconciliation_views()` rebuilds the facts in graph order. Post-import application
orchestration invokes its stages separately, so a slow stage is measurable and does not hold one
large application transaction. The system audit runs asynchronously after import finalization has
committed; an audit failure cannot roll back canonical imported data.

For exact current view names and definitions, inspect `src/main/resources/sql/migration`.

## SQL migration boundaries

`V01.000__schema.sql` defines the core schema: reference tables, enums, raw ledger tables
(`accounts`, `positions`, `cash_operations`), import provenance, price/FX tables, and base functions.

`V01.001__functions.sql` and `V01.002__triggers.sql` define database behavior. `V01.003__initial_data.sql`
seeds the anonymized sample portfolio/configuration and public/reference market data. `V01.004__asset_price_history_import.sql`
adds baseline asset mappings and price observations.

`V01.005__portfolio_views.sql` contains the production calculation layer: the FX resolver
`resolve_fx_rate()`, the canonical FX usable-status helper `fx_status_usable()`, production
calculation views, and the seven production materialized views: `account_monthly_mv`,
`portfolio_monthly_mv`, `account_statistics`, `portfolio_kpi_summary`, `portfolio_currency_breakdown`,
`portfolio_asset_allocation`, and `symbol_performance`. `refresh_reporting_views()` refreshes only
those objects in explicit order.

`V01.006__reconciliation_views.sql` contains read-only reconstruction and diagnostic objects and the
public naming aliases. `refresh_reconciliation_views()` refreshes reconciliation materializations on
demand; they are not production refresh dependencies. `V01.007__persisted_system_audit.sql` remains the
persisted audit subsystem and consumes reconciliation review data.

`V01.008__import_provenance.sql` adds immutable broker artifact/row evidence, nullable canonical-row
provenance links, immutable-evidence triggers, and provenance diagnostics. It does not alter financial
formulas or account/asset identity.

Integration ports are under `com.smartbox.investory.integration`. The current first adapter is
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
`recon_v_`; neither alias family is a financial source of truth. `accounts.id` is an internal generated
identity. Broker identifiers are stored in `accounts.external_account_id` and are unique by provider.
Raw account, position, and cash-ledger history is protected from account or portfolio deletion; derived
projection rows may be deleted and rebuilt.

## FX usable-status contract

Authoritative monetary conversion uses a single helper, `investory.fx_status_usable(status)`, defined in
`V01.005__portfolio_views.sql`. It treats `OK`, `ESTIMATED`, and `SAME_CURRENCY` as usable and
`STALE`, `MISSING_RATE`, `MISSING_CURRENCY` as not usable. The usable-status list is not duplicated or
patched after the fact; production views and materialized views call the helper directly. Historical
interpolation in `resolve_fx_rate()` is bracketed deterministically by the nearest observation strictly
before and strictly after the valuation date within one source/method series, and only before the
`fx_configuration.daily_history_start` boundary.

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

The dashboard consumes these existing reporting services through `DashboardFacade`. The facade maps
their results into UI-specific view models; it is not a second reporting source of truth and does not
change the `account_daily` boundary.

## Migration baseline freeze

The squashed database/accounting baseline `V01.000`–`V01.008` is frozen. Do not edit these migrations
after the freeze point. Future schema/data migration changes are append-only and start with the next
available Flyway version, currently `V01.009` (which does not yet exist).

A frozen baseline may be reopened only for a proven defect that requires deliberate baseline
regeneration, not for routine feature work.
