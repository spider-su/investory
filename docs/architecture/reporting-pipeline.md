# Reporting pipeline

This document defines the stable data lineage from imported ledger rows to reporting surfaces.
Exact view definitions and column lists live in Flyway migrations.

## Data lineage

```text
broker imports
  -> positions / cash_operations / accounts / assets / exchange_rates
  -> normalized cash ledger and position valuation
  -> account_daily
  -> portfolio/account reporting views and materialized views
  -> PortfolioService / dashboard / compatibility APIs / exports
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

Asset and currency semantics are defined by:

- `docs/domain/asset-identity-and-money.md`
- `docs/domain/fx-normalization.md`

## Daily projection boundary

`account_daily` is the persisted daily read-model boundary. Each row represents one account and one
date and combines end-of-day state with that day's flows and valuation.

It is live projection data, not disposable legacy history.

When imported dates, ledger classification, market prices, or FX data change, rebuild the projection
instead of patching dashboard calculations.

## Higher reporting layers

Portfolio and monthly summaries above `account_daily` are database-derived views/materialized views.
Important consumers include portfolio KPI, asset allocation, currency breakdown, performance, and
reconciliation surfaces.

These layers are snapshots/derived projections, not independent sources of financial truth. They must
be refreshed after inputs that affect valuation or classification change.

For exact current view names and definitions, inspect `src/main/resources/sql/migration`.

## SQL migration boundaries

`V01.002__checks_and_views.sql` contains production calculation functions, views, and the seven
production materialized views: `account_monthly_mv`, `portfolio_monthly_mv`, `account_statistics`,
`portfolio_kpi_summary`, `portfolio_currency_breakdown`, `portfolio_asset_allocation`, and
`symbol_performance`. `refresh_reporting_views()` refreshes only those objects in explicit order.

`V01.004__reconciliation_views.sql` contains read-only reconstruction and diagnostic objects.
`refresh_reconciliation_views()` refreshes reconciliation materializations on demand; they are not
production refresh dependencies. `V01.005__persisted_system_audit.sql` remains the persisted audit
subsystem and consumes reconciliation review data.

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
