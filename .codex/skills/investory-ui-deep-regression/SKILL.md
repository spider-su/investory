---
name: investory-ui-deep-regression
description: Run Investory's slow, high-coverage browser and database regression suite for CRUD, simulations, dashboard values, persistence, validation, and reconciliation.
metadata:
  short-description: Deep Investory UI regression
---

# Investory deep UI regression

Use this skill before merge, for release confidence, or when route smoke and interaction checks expose a functional risk.
For page-specific behavior, route through the canonical `qa/ui/` smoke, active, and facts scenarios;
do not copy their assertions into this skill.

## Test layers

Run the narrowest relevant layer first, then expand:

1. `UiPageSmokeIT` for all route rendering and browser-error checks.
2. `HappyInvestorReadOnlyUiIT` for canonical read-only financial values and presentation rules.
3. `LongTermAssetCrudUiIT` for browser-to-database asset lifecycle coverage.
4. `PlanSimulationCrudUiIT` for plan creation, revision persistence, navigation, and validation.
5. `InvestmentDashboardGoldenUiIT` for dashboard values against view models and database state.

Read `docs/development/testing.md` before changing scope. Use `-pl app -am`, the repo-local Maven cache, quoted PowerShell properties, and the configured Docker host from the repository instructions.

## Data and safety

- Use disposable fixtures or an explicitly approved test database.
- Treat JDBC/API readiness as supporting evidence only; UI behavior requires a browser assertion.
- Do not submit mutations to a user's live portfolio during regression unless the user explicitly requests it and the flow is reversible.
- Reconcile displayed financial values with independent HappyInvestor facts and document exact-value
  versus presentation-rounding rules. Do not convert a documented display rounding boundary into a
  product failure.

## Timeouts and parallelism

- Start the application and browser once per suite where possible.
- Use 20 seconds for ordinary navigation/actions and 120 seconds for known slow projection or simulation pages.
- Wait on specific URLs, responses, or readiness markers. Avoid fixed sleeps and `networkidle`.
- Parallelize independent read-only routes only after confirming fixture isolation. Keep CRUD, archive/reactivate, and revision lifecycle tests sequential.
- Preinstall Playwright browsers and prevent concurrent driver installation to avoid `__dirlock` failures.

## Diagnostics

Keep build/classpath failures separate from UI failures. When a `NoClassDefFoundError`, missing
class file, or stale API type appears, compare source, module target output, and repo-local
dependency JAR timestamps; run a clean reactor rebuild before rerunning. If the clean build still
fails, preserve the exact blocker and do not claim a green suite or infer a route defect.

## Report

Use the repository's documented Failsafe command for the Java browser suite; use Playwright MCP for
deployed exploratory missions. Return a concise summary by layer with passed, failed, skipped, and
blocked counts. For each issue include exact test/page/control, expected versus actual behavior,
root-cause category, command, and artifact path. State whether live data changed and list unrelated
dirty files preserved.
