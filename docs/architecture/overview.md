# Architecture overview

This document describes stable Investory system boundaries. Exact classes, endpoints, configuration
keys, dependency versions, and schema objects should be read from the current source, configuration,
and Flyway migrations.

## System shape

Investory is a Spring Boot monolith backed by PostgreSQL. The main data path is:

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

- `controllers`: UI, import/export/admin endpoints, Telegram, and compatibility endpoints.
- `application/dashboard`: dashboard query boundary, facade orchestration, and immutable dashboard
  view models for the server-rendered UI.
- `application/longterm`: manual non-brokerage asset entry, economics, and projection inputs for real
  estate, bonds, deposits, cash reserve, and other long-term assets.
- `application/profile`: read-only `InvestmentProfile` aggregation of market portfolio and manual
  long-term assets.
- `application/simulation`: deterministic nominal retirement projection, scenario assumptions, funding
  strategy, reserve/harvest policy, and simulation-only yearly results.
- `application/planning`: planning-year lifecycle, baseline persistence, historical/current actuals,
  current-year bridge, and Actual/Live/Projected timeline orchestration.
- `services`: portfolio calculations, market/FX sync, projections, tax, cash flow, and pricing.
- `services/imports`: broker parser SPI and broker-specific import implementations.
- `services/openai`: optional AI/Telegram analysis and scheduled reports.
- `services/portfolio`: deterministic portfolio command handling.
- `services/notifications`: notification and alert rules.
- `infrastructure`: persistence entities, repositories, enums, and reporting projections.
- `clients`: external HTTP clients.
- `config`: application security, scheduling, Thymeleaf, Telegram, and AI configuration.

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

Dependency direction is intentionally one way:

```text
accounting + long-term assets -> profile -> planning/simulation -> presentation
```

Planning must not become an accounting source of truth or depend on a projected result as an actual fact.

Brokerage reporting is exposed to higher-level application code through focused immutable read models
under `services/portfolio/read`. `BrokeragePortfolioReadService` currently publishes a shared
brokerage snapshot for `InvestmentProfile`; it is not multi-portfolio scoped. Manual long-term assets
join only while `InvestmentProfileFacade` composes the profile. Profile and planning code do not
mutate accounting state.

## Change rules

- Prefer existing architecture unless a concrete requirement makes it insufficient.
- Do not recreate removed tables, endpoints, or parallel sources of truth as shortcuts.
- Before deleting a table, view, endpoint, or entity, search its Java, SQL, dashboard, scheduler,
  export/reset, and test consumers.
- A domain-semantic change must update the matching contract in `docs/domain/`.
