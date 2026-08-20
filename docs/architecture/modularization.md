# Maven modularization

Investory is a modular monolith: one Git repository, one Spring Boot executable, one JVM/process, one
PostgreSQL database, and one deployment. The root Maven project is an aggregator; it does not contain
application Java sources.

## Reactor and module ownership

- `modules/shared`: domain-neutral currency, portfolio-context, and presentation contracts.
- `modules/investment`: brokerage accounting, imports, market data, reporting, reconciliation, and
  public Investment read contracts.
- `modules/longterm`: manually managed long-term assets and public Long-Term read contracts.
- `modules/retirement`: Investment Profile composition, planning, and deterministic simulation.
- `integrations`: optional external integrations, notifications, and their persistence adapters.
- `test-support`: reusable Investment fixtures, PostgreSQL Testcontainers support, and the committed
  fast-test database snapshot.
- `adapters/web-ui`: server-rendered controllers, Thymeleaf templates, static assets, shared UI
  presentation, and UI-focused tests.
- `app`: Spring Boot composition, runtime configuration, security, scheduling, Flyway resources,
  executable packaging, and application-wide integration tests.

## Dependency direction

```text
investment -> shared
longterm -> shared
retirement -> shared + investment public API + longterm public API
integrations -> investment public/integration contracts
web-ui -> investment + longterm + retirement
test-support -> shared + investment
app -> shared + investment + longterm + retirement + integrations + web-ui
```

`test-support` is consumed only as a test-scoped dependency by application, UI, and integration
modules. Maven dependencies provide compile-time module boundaries; `LayerDependencyTest` additionally
restricts Retirement to Investment and Long-Term public packages and prevents UI access to domain
infrastructure or repositories.

Investment does not depend on Long-Term or Retirement. Long-Term does not depend on Investment or
Retirement and does not use Investment persistence. Retirement does not use Investment or Long-Term
infrastructure/persistence. Application composition is the only executable packaging layer.

Domain services and domain-focused tests live in their owning module. Server-rendered feature
controllers, templates, static assets, and UI-focused tests live in `adapters/web-ui`. External
adapters, notifications, and optional integrations live in `integrations`; tightly coupled
market/FX/export adapters remain inside Investment behind its infrastructure boundary.

Flyway migrations and application configuration remain in `app`. Schema isolation is intentionally not
part of this work. The next separate epic is **Database Module Isolation**, covering core, investment,
longterm, and retirement PostgreSQL schemas, producer-owned read views, and optional DB-level permission
isolation.
