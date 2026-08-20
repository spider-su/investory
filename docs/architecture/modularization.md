# Maven modularization

Investory is a modular monolith: one Git repository, one Spring Boot executable, one JVM/process, one
PostgreSQL database, and one deployment. The root Maven project is an aggregator; it does not contain
application Java sources.

## Reactor

```text
app
├── shared
├── investment
├── longterm
├── retirement
├── integrations
└── adapters/web-ui

test-support -> investment (test fixtures only)
investment -> shared
longterm -> shared
retirement -> shared
retirement -> investment public API
retirement -> longterm public API
integrations -> investment public/integration contracts
```

Investment does not depend on Long-Term or Retirement. Long-Term does not depend on Investment or
Retirement and does not use Investment persistence. Retirement does not use Investment or Long-Term
infrastructure/persistence.
Application composition is the only executable packaging layer.

Feature controllers and tests live with their owning module. External adapters, notifications, and
optional integrations live in `integrations`; tightly coupled market/FX/export adapters remain inside
Investment behind its infrastructure boundary.

Flyway migrations and application configuration remain in `app`. Schema isolation is intentionally not
part of this work. The next separate epic is **Database Module Isolation**, covering core, investment,
longterm, and retirement PostgreSQL schemas, producer-owned read views, and optional DB-level permission
isolation.
