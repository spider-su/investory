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
├── profile
├── retirement
├── integrations
├── adapters/web-ui
└── test-support (test scope)

investment -> shared
longterm -> shared
profile -> shared + investment public API + longterm public API
retirement -> shared + investment/longterm/profile public APIs
integrations -> investment public/integration contracts
adapters/web-ui -> investment/longterm/profile/retirement/integrations public APIs
test-support -> shared + investment + profile (fixtures and PostgreSQL test infrastructure only)
```

Investment does not depend on Long-Term or Retirement. Long-Term does not depend on Investment or
Retirement and does not use Investment persistence. Retirement does not use Investment or Long-Term
infrastructure/persistence.
Application composition is the only executable packaging layer. `test-support` is consumed with
Maven test scope by feature modules and `app`; it is not packaged as production behavior.

Feature controllers and tests live with their owning module. External adapters, notifications, and
optional integrations live in `integrations`; tightly coupled market/FX/export adapters remain inside
Investment behind its infrastructure boundary.

Shared utility wrappers are allowed only when they preserve module-level vocabulary or isolate a
third-party dependency. `shared.util.StringUtils.isBlank` is intentionally a one-line wrapper: it
keeps callers independent of the selected text library and preserves the stable shared import. Do
not expand it with pass-through methods that lack the same boundary value.

Business-owned REST controllers live under each module's `web` package. Server-rendered MVC
controllers remain in `adapters/web-ui` and depend on UI-side client interfaces. Active in-process
clients inject the owning module public API, not its REST controller. A future HTTP client can replace
an in-process implementation without changing MVC controllers.

## Architecture decision: UI uses module public APIs in-process

**Decision:** Server-rendered UI client implementations may inject and call a module's public API
directly in-process. Business REST controllers remain external adapters only; MVC/UI code must not
inject those controllers as its application boundary.

This is intentional for the modular monolith. The UI and the business modules run in the same
process, so the in-process path avoids an unnecessary HTTP and serialization hop while preserving
the module `api` contract. The replaceable UI client interface keeps a future HTTP implementation
possible without changing MVC controllers.

Therefore, a UI client injecting a module public API is an accepted architecture choice, not a
boundary violation or a defect. The boundary violation is direct MVC/UI coupling to a business REST
controller or to business infrastructure.

Flyway migrations and application configuration remain in `app`. Schema isolation is intentionally not
part of this work. The next separate epic is **Database Module Isolation**, covering core, investment,
longterm, and retirement PostgreSQL schemas, producer-owned read views, and optional DB-level permission
isolation.
