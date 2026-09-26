# Business modules --- quick KT index

Investory is a modular monolith. The four business modules own domain
behavior and expose Java APIs. They run in one Spring Boot process, but their
dependency direction still matters.

``` text
investment -> shared
longterm   -> shared
profile    -> shared + investment public API + longterm public API
retirement -> shared + investment/longterm/profile public APIs
```

The diagram is simplified. The authoritative dependency graph is in
[`docs/architecture/modularization.md`](../architecture/modularization.md):
Investment and Long-Term do not depend on each other; Profile composes public
Investment and Long-Term reads; Retirement consumes public APIs.

## KT pages

- [`investment-module-quick-kt.md`](investment-module-quick-kt.md) --- imported brokerage ledger, valuation, projections, reporting, and reconciliation.
- [`longterm-module-quick-kt.md`](longterm-module-quick-kt.md) --- manually managed property, bond, cash-reserve, and personal assets.
- [`profile-module-quick-kt.md`](profile-module-quick-kt.md) --- whole-wealth profile facts composed for planning.
- [`retirement-module-quick-kt.md`](retirement-module-quick-kt.md) --- plans, timeline, deterministic simulation, and analysis.
- [`simulation-module-quick-kt.md`](simulation-module-quick-kt.md) --- deeper simulation-specific rules and invariants.

## Supporting module READMEs

- [`modules/shared/README.md`](../../modules/shared/README.md) --- shared vocabulary and technical
  support boundaries.
- [`integrations/README.md`](../../integrations/README.md) --- external providers, jobs, and
  notification adapters.
- [`modules/ryczalt/README.md`](../../modules/ryczalt/README.md) --- native accounting runtime and
  staged migration boundary.
- [`app/README.md`](../../app/README.md) --- executable composition, configuration, security, and
  migrations.
- [`adapters/web-ui/README.md`](../../adapters/web-ui/README.md) --- server-rendered MVC, in-process
  clients, templates, and presentation boundaries.
- [`test-support/README.md`](../../test-support/README.md) --- deterministic fixtures and database
  test infrastructure.

Supporting boundaries are documented in
[`docs/architecture/modularization.md`](../architecture/modularization.md).
Do not put business rules in `adapters/web-ui` or REST controllers.
