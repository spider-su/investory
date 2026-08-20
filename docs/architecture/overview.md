# Architecture overview

This document describes stable Investory system boundaries. Exact classes, endpoints, configuration
keys, dependency versions, and schema objects should be read from the current source, configuration,
and Flyway migrations.

## System shape

Investory is one Spring Boot modular monolith, one executable application and one deployment,
backed by one PostgreSQL database. The main data path is:

```text
broker files
  -> import/parsing
  -> normalized portfolio ledger
  -> market prices + FX
  -> daily account projection
  -> database reporting layers
  -> portfolio services
  -> InvestmentProfile
     -> dashboard / APIs / exports / notifications
     -> planning -> deterministic retirement simulation -> Actual / Live / Projected timeline

manual long-term assets
  -> InvestmentProfile
```

The application uses Java, Maven, Spring Data JPA, Flyway, Thymeleaf, and Chart.js. PostgreSQL schema
changes are owned by Flyway.

## Main code areas

- `investment`: brokerage imports, accounting, market/FX, reporting/dashboard, reconciliation, and
  Investment persistence. Its cross-domain reads are interfaces and immutable records in
  `investment.api`; repository-backed readers live in `investment.infrastructure.read`.
- `longterm`: manual non-brokerage asset application services, persistence, and public read contracts
  in `longterm.api`.
- `retirement`: profile composition, planning, simulation, and their persistence adapters.
- `shared`: domain-neutral currency conversion, portfolio context, and presentation primitives exposed
  from the Investment public surface where required by the current Maven layout.
- `integrations`: external adapters plus integration and notification persistence.
- `app`: application composition, security, scheduling, executable packaging, Flyway resources, and
  global UI concerns.

Use the current package tree as the source of truth if these boundaries change.

## Import boundary

Uploaded broker artifacts and parsed broker rows are retained as immutable provenance evidence in
`import_source_files` and `import_source_rows`. Broker input is then normalized before it becomes
reporting data. Source symbols and broker identifiers remain provenance; canonical asset identity is
defined by `docs/domain/asset-identity-and-money.md`.

The retained source evidence is not the accounting truth. Normalized/canonical rows in the portfolio
ledger are the accounting state consumed by projections and reporting. Investory therefore preserves
source evidence for audit and reprocessing without claiming to be a full immutable event-sourcing
system.

## Persistence boundary

PostgreSQL plus Flyway own the schema. JPA does not create or mutate the production schema.

Raw portfolio state is persisted in tables such as accounts, assets, positions, cash operations,
prices, FX rates, and import history. `account_daily` is the persisted reporting boundary above the
raw ledger. Higher portfolio summaries are database-derived reporting layers rather than independent
application-owned truth.

Planning persistence is separate from accounting persistence. Planning-year values and baselines are
downstream snapshots/overrides; they do not alter positions, cash operations, `account_daily`, market
prices, FX, or API response data. Manual long-term assets feed `InvestmentProfile` but do
not become brokerage accounting rows. Simulation transfers are allocation state only, never real trades.

See `docs/architecture/reporting-pipeline.md` for the reporting lineage.

## Runtime boundaries

The application exposes:

- the Investory dashboard and landing page;
- broker import, export, and administrative operations;
- optional Telegram notifications and commands;
- optional OpenAI-backed analysis/reporting.

For exact current endpoint and security behavior, use the controllers and security configuration.
For the product-level deployment status, see `README.md`.

Dashboard page data follows this application flow:

```text
HomeController
  -> DashboardQuery
  -> DashboardFacade
  -> existing portfolio/benchmark services and period filtering
  -> DashboardPageView section models
  -> Thymeleaf dashboard and Chart.js presentation code
```

This is an application boundary for the internal UI, not a new dashboard JSON API. See
`docs/architecture/dashboard-application.md` for the dashboard-specific details.

## Domain boundary direction

Investory remains one Spring Boot application and one JVM process. Maven provides compile-time module
boundaries while the business ownership is organized around three domains:

- **Investment** owns brokerage accounts, imports, accounting, market data, portfolio reporting,
  dashboard, and reconciliation.
- **Long-Term Assets** owns manually managed real estate, bonds, deposits, cash reserves, and other
  assets.
- **Retirement** owns profile composition, planning, simulation, scenarios, and progress tracking.

The intended dependency direction is:

```text
Retirement -> Investment public read API
Retirement -> Long-Term Assets public read API

Investment -> shared primitives/contracts
Long-Term Assets -> shared primitives/contracts
```

Investment and Long-Term Assets do not depend on each other or on Retirement. Retirement depends on
Investment and Long-Term only through their `api` packages. A public boundary exposes focused,
immutable business read models, never JPA entities, repositories, SQL projections, or internal
accounting services. Shared contracts stay small and domain-neutral.

`investment.api.BrokeragePortfolioReader` publishes immutable shared brokerage snapshots for
profile composition; its Spring implementation is
`investment.infrastructure.read.BrokeragePortfolioReadService` and it is not multi-portfolio scoped.
`BrokerageAssetClassificationReader` supplies
the optional symbol classification needed by the profile, and `HistoricalPortfolioActualsReader`
supplies portfolio-scoped calendar-year planning facts. Their Investment implementations retain
persistence access behind the boundary.

`shared.portfolio.PortfolioContextReader` supplies optional portfolio identity and base currency for
Long-Term validation and aggregation. The Investment-owned repository-backed implementation preserves
the existing `PortfolioKpiSummaryRepository` lookup and missing-portfolio behavior.

Integration entities and repositories are owned by `integrations.infrastructure.persistence`; notification
state is owned by `integration.notifications.infrastructure`. They keep their existing database mappings
and remain adapters outside the three business domains.

Long-Term publishes `LongTermAssetProfileReader` for profile composition and projection inputs, and
`LongTermAssetAnnualSnapshotReader` for current and historical planning facts. Its public immutable
models include the stable Long-Term asset and cash-flow enums. Retirement callers use these contracts;
Long-Term persistence and application services remain internal.

Planning must not become an accounting source of truth or depend on a projected result as an actual fact.

`shared.currency.CurrencyType` and `shared.currency.CurrencyConversion` now provide the shared FX
boundary. `CurrencyRateService` implements the BigDecimal conversion contract; its rate resolution,
persistence, cache, and double compatibility APIs remain Investment implementation details. Long-Term
and Retirement consumers use the shared contract.

`app.UiPresentation` is an application UI helper. Long-Term uses only the shared financial presentation
primitive; no PlanningPresentation exception or Long-Term-to-Retirement presentation dependency remains.
`SimulationPlanService` is the only documented simulation persistence orchestration adapter; deterministic
simulation classes remain persistence-free.

The Maven reactor contains `app`, `investment`, `longterm`, `retirement`, `integrations`, and
`test-support`. Retirement consumes Investment and Long-Term public APIs. Investment and Long-Term do not
depend on Retirement. This remains one executable application, not microservices. PostgreSQL schema
isolation is a separate future epic; this modularization does not change schemas, roles, or Flyway history.

## Change rules

- Prefer existing architecture unless a concrete requirement makes it insufficient.
- Do not recreate removed tables, endpoints, or parallel sources of truth as shortcuts.
- Before deleting a table, view, endpoint, or entity, search its Java, SQL, dashboard, scheduler,
  export/reset, and test consumers.
- A domain-semantic change must update the matching contract in `docs/domain/`.
