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

## Current phase policy

Browser scenarios use `2560x1440` only. They prioritize financial reconciliation and functional
bug detection: canonical Happy Investor values, reporting/domain contracts, totals, allocations,
income, returns, cash flows, positions, long-term assets, period filtering, disclosures, dialogs,
tooltips, tables, filters, navigation, and parent/detail consistency. Runtime health covers HTTP,
console, failed first-party requests, unexpected read-only mutations, and obvious clipping or
overlap at the supported desktop viewport. Alternate viewport, breakpoint, touch, and runtime
resize coverage are out of scope unless explicitly requested.

Use these finding classifications consistently: `PASS`, `EXPECTED ROUNDING / LIVE DATA DRIFT`,
`STALE CONTRACT / TEST DEFECT`, `PRODUCT DEFECT`, `BUILD DEFECT`, `ENVIRONMENT BLOCKER`, and
`SUSPICIOUS / NEEDS RECONCILIATION`. Optional or out-of-scope checks that cannot run do not make
the whole page suspicious.

## Financial source-of-truth rule

UI tests follow one source-of-truth chain: `Happy Investor story → scenario / source-operation
ledger → domain and reporting contracts → canonical facts → UI expectations`. They must not redefine
financial metric semantics locally, infer formulas from labels, or maintain independent hardcoded
interpretations for deposits, withdrawals, invested/net deposits, Income Base, investment result,
TWR, XIRR, benchmark availability, net worth, long-term income, allocation, or simulation metrics.
For a mismatch, identify the story fact, reconcile the source ledger, locate the authoritative
contract, derive or confirm the canonical expected value, and only then classify the UI/backend.
Current Yahoo prices, FX, and other live observations are non-deterministic and must be reported
separately from fixed fixture facts.

HTTP 200, `DOMContentLoaded`, or main-page readiness does not imply that async financial widgets
are ready. Wait for component-level loaded/completed markers, expected response completion, or
populated values before checking them. Treat `—`, empty series/tables, and loading states as
interim until settled; avoid fixed sleeps and `networkidle`.
