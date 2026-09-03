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
- [`longterm-module-quick-kt.md`](longterm-module-quick-kt.md) --- manually managed property, bond, deposit, and cash-reserve assets.
- [`profile-module-quick-kt.md`](profile-module-quick-kt.md) --- whole-wealth profile facts composed for planning.
- [`retirement-module-quick-kt.md`](retirement-module-quick-kt.md) --- plans, timeline, deterministic simulation, and analysis.
- [`simulation-module-quick-kt.md`](simulation-module-quick-kt.md) --- deeper simulation-specific rules and invariants.

Supporting boundaries are documented in
[`docs/architecture/modularization.md`](../architecture/modularization.md).
Do not put business rules in `adapters/web-ui` or REST controllers.
