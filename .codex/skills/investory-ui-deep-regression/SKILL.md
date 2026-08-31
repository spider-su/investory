---
name: investory-ui-deep-regression
description: Run Investory's slow, high-coverage browser and database regression suite for CRUD, simulations, dashboard values, persistence, validation, and reconciliation.
metadata:
  short-description: Deep Investory UI regression
---

# Investory deep UI regression

Use this skill before merge, for release confidence, or when route smoke and interaction checks expose a functional risk.

## Test layers

Run the narrowest relevant layer first, then expand:

1. `UiPageSmokeIT` for all route rendering and browser-error checks.
2. `LongTermAssetCrudUiIT` for browser-to-database asset lifecycle coverage.
3. `PlanSimulationCrudUiIT` for plan creation, revision persistence, navigation, and validation.
4. `InvestmentDashboardGoldenUiIT` for dashboard values against view models and database state.

Read `docs/development/testing.md` before changing scope. Use `-pl app -am`, the repo-local Maven cache, quoted PowerShell properties, and the configured Docker host from the repository instructions.

## Data and safety

- Use disposable fixtures or an explicitly approved test database.
- Treat JDBC/API readiness as supporting evidence only; UI behavior requires a browser assertion.
- Do not submit mutations to a user's live portfolio during regression unless the user explicitly requests it and the flow is reversible.
- Reconcile displayed financial values with the same fixture database and document rounding/formatting rules.

## Timeouts and parallelism

- Start the application and browser once per suite where possible.
- Use 20 seconds for ordinary navigation/actions and 120 seconds for known slow projection or simulation pages.
- Wait on specific URLs, responses, or readiness markers. Avoid fixed sleeps and `networkidle`.
- Parallelize independent read-only routes only after confirming fixture isolation. Keep CRUD, archive/reactivate, and revision lifecycle tests sequential.
- Preinstall Playwright browsers and prevent concurrent driver installation to avoid `__dirlock` failures.

## Diagnostics

Keep build/classpath failures separate from UI failures. When a `NoClassDefFoundError` or stale API type appears, compare source, module target output, and repo-local dependency JAR timestamps; rebuild/install the reactor before rerunning. Preserve failure artifacts and do not claim a green suite when any layer is blocked.

## Report

Return a concise summary by layer with passed, failed, skipped, and blocked counts. For each issue include exact test/page/control, expected versus actual behavior, root-cause category, command, and artifact path. State whether live data changed and list unrelated dirty files preserved.
