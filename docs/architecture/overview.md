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

## Domain boundary direction

Investory remains one Spring Boot application and one Maven artifact. The business ownership target is
three domains:

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

Investment and Long-Term Assets must not depend on Retirement. Long-Term Assets must not depend on
Investment implementation or persistence. Retirement must not access Investment or Long-Term Assets
persistence. A public boundary exposes focused, immutable business read models, never JPA entities,
repositories, SQL projections, or internal accounting services. Shared contracts stay small and
domain-neutral; current candidates are currency identifiers/conversion and time abstractions only when
their actual consumers require them.

Brokerage reporting already has a focused read boundary under `services/portfolio/read`.
`BrokeragePortfolioReadService` publishes immutable shared brokerage snapshots for profile composition;
it is not multi-portfolio scoped. It may use Investment internals behind that boundary. Manual Long-Term
Assets join while `InvestmentProfileFacade` composes the profile. Profile and planning code do not
mutate accounting state.

`BrokerageAssetClassificationReader` supplies the optional symbol classification required by profile
composition. `HistoricalPortfolioActualsReader` supplies complete, portfolio-scoped calendar-year facts
for planning and reconciliation. Their Investment-owned implementations retain `AssetRepository` and
`PortfolioMonthlyPerformanceRepository` access; the public contracts and immutable models do not expose
persistence types.
`BrokeragePortfolioContextReader` supplies optional portfolio identity and base currency for Long-Term
validation and aggregation while retaining the existing `PortfolioKpiSummaryRepository` lookup inside
Investment.

Planning must not become an accounting source of truth or depend on a projected result as an actual fact.

### Transitional boundary exceptions

The target is not fully implemented yet. These exact current dependencies are intentional work items,
not permitted broad package exceptions:

| Current dependency | Why it exists now | Removal stage |
| --- | --- | --- |
| `LongTermAssetService` -> `PlanningPresentation` | Long-Term template-visible summaries use the cross-domain formatter. | Stage 5 |
| `application.longterm.RentalIncomeProjection` -> `application.profile.ProjectedLongTermAsset` | Long-Term projection currently uses the profile-owned model. | Stage 6 |
| `InvestmentProfileFacade` -> `LongTermAssetService` | Profile composition still reads Long-Term implementation directly. | Stage 6 |
| `HistoricalLongTermAssetYearSource` and Retirement simulation callers -> `LongTermAssetService`/Long-Term types | Retirement has no Long-Term public read contract yet. | Stage 6 |
| `PlanningPresentation` -> planning, profile, simulation, and Long-Term types | The formatter is a cross-domain presentation bridge; Long-Term also calls it. | Stage 5 |

`shared.currency.CurrencyType` and `shared.currency.CurrencyConversion` now provide the shared FX
boundary. `CurrencyRateService` implements the BigDecimal conversion contract; its rate resolution,
persistence, cache, and double compatibility APIs remain Investment implementation details. Long-Term
and Retirement consumers use the shared contract.

ArchUnit rules protect the boundaries that already exist. The one accounting-to-Retirement exception is
specific to `PlanningPresentation`, documented above, and is removed in Stage 5. Later stages replace
the listed direct dependencies with narrow public read contracts before packages are consolidated.

## Change rules

- Prefer existing architecture unless a concrete requirement makes it insufficient.
- Do not recreate removed tables, endpoints, or parallel sources of truth as shortcuts.
- Before deleting a table, view, endpoint, or entity, search its Java, SQL, dashboard, scheduler,
  export/reset, and test consumers.
- A domain-semantic change must update the matching contract in `docs/domain/`.
