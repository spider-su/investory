# Page-focused UI scenarios

These scenarios hold page-specific routes, controls, facts, and composition. Generic UI skills
discover and execute them through Playwright MCP.

The application-wide inventory and current execution status are in
[`application-read-only-baseline-report.md`](application-read-only-baseline-report.md). Its
smoke, active, and regression scenario contracts are the corresponding application baseline files
in this directory.

- [`smoke/`](smoke/): fast route/rendering scenarios.
- [`active/`](active/): safe interactive-control scenarios.
- [`facts/`](facts/): rendered domain facts and reconciliation scenarios.
- [`regression/`](regression/): composition of the three layers plus page-specific regression gaps.

Scenario files reference canonical domain documentation, existing UI tests, and independent
HappyInvestor facts rather than creating a second source of truth.
