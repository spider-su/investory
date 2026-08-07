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
  -> dashboard / APIs / exports / notifications
```

The application uses Java, Maven, Spring Data JPA, Flyway, Thymeleaf, and Chart.js. PostgreSQL schema
changes are owned by Flyway.

## Main code areas

- `controllers`: UI, import/export/admin endpoints, Telegram, and compatibility endpoints.
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

Broker input is normalized before it becomes reporting data. The important rule is that source
symbols and broker identifiers are provenance; canonical asset identity is defined by
`docs/domain/asset-identity-and-money.md`.

Import auditing records file status and counts. Normalized rows are the operational ledger; Investory
is not an immutable raw-event store and does not retain the original broker files as its source of
truth.

## Persistence boundary

PostgreSQL plus Flyway own the schema. JPA does not create or mutate the production schema.

Raw portfolio state is persisted in tables such as accounts, assets, positions, cash operations,
prices, FX rates, and import history. `account_daily` is the persisted reporting boundary above the
raw ledger. Higher portfolio summaries are database-derived reporting layers rather than independent
application-owned truth.

See `docs/architecture/reporting-pipeline.md` for the reporting lineage.

## Runtime boundaries

The application exposes:

- the Investory dashboard and landing page;
- broker import, export, and administrative operations;
- Ghostfolio-compatible endpoints; the `ghostfolio` profile adds a dedicated permissive security chain for frontend compatibility development;
- optional Telegram notifications and commands;
- optional OpenAI-backed analysis/reporting.

For exact current endpoint and security behavior, use the controllers and security configuration.
For the product-level deployment status, see `README.md`. For compatibility details, see
`docs/integrations/ghostfolio.md`.

## Change rules

- Prefer existing architecture unless a concrete requirement makes it insufficient.
- Do not recreate removed tables, endpoints, or parallel sources of truth as shortcuts.
- Before deleting a table, view, endpoint, or entity, search its Java, SQL, dashboard, scheduler,
  export/reset, and test consumers.
- A domain-semantic change must update the matching contract in `docs/domain/`.
