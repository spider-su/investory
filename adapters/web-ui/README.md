# Web UI adapter module

`web-ui` owns the server-rendered MVC boundary: page controllers, in-process client adapters,
Thymeleaf view models/templates, and browser-facing presentation behavior. It is an adapter layer,
not a source of accounting, planning, or investment-domain truth.

## Boundary rules

- Call business modules through their published APIs or focused client interfaces.
- Do not inject business REST controllers, repositories, JPA entities, or infrastructure services.
- Keep formatting and page composition here; keep financial calculations in the owning business
  module.
- Use stable semantic selectors and page-ready markers for browser tests.
- Preserve exact domain values at the API/view-model boundary and apply display rounding only in the
  presentation layer.

The current UI runs in-process inside the modular monolith. Client interfaces keep a future HTTP
transport possible without changing MVC controllers.

See [`docs/architecture/dashboard-application.md`](../../docs/architecture/dashboard-application.md),
[`docs/architecture/modularization.md`](../../docs/architecture/modularization.md), and the UI
skills under [`/.codex/skills/`](../../.codex/skills/).
