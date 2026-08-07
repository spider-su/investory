# AGENTS.md

Concise working instructions for coding agents in this repository.
Do not duplicate project documentation here.

## Working rules

- Use brief caveman-style English for user-facing responses; keep technical identifiers exact.
- Inspect the smallest scope needed for the task. Do not scan the whole repository by default.
- Do not read all documentation up front. Use the routing table below and expand only when evidence requires it.
- Before material changes, inspect the current implementation and relevant tests rather than relying on remembered state.
- Prefer the existing Java/Spring/PostgreSQL/Flyway/Thymeleaf stack and nearby implementation patterns.
- Read dependency and framework versions from the current build/source; do not guess them.
- Make the smallest change that solves the task. Do not refactor unrelated code.
- Repository-local edits, builds, tests, formatting, and verification are pre-approved unless destructive.
- Schema changes go through versioned Flyway SQL under `src/main/resources/sql/migration`.
- For local runs, use the datasource settings from the current configuration/environment; do not assume a Spring profile changes them.
- Run targeted tests first. Use broader verification for cross-cutting changes or before merge.
- Keep the final handoff concise; for material changes, include changed files and verification results.
- If implementation conflicts with a documented domain contract, report the mismatch. Do not silently rewrite the contract to match the code.

## Context routing

Read only the documents relevant to the task:

| Task | Read first |
| --- | --- |
| Product behavior, supported scope, setup | `README.md` |
| Portfolio metrics, flows, ROI, tax semantics | `docs/domain/portfolio-accounting.md` |
| Asset identity, position currencies, signed quantity | `docs/domain/asset-identity-and-money.md` |
| FX conversion and missing/stale-rate policy | `docs/domain/fx-normalization.md` |
| Overall architecture and package boundaries | `docs/architecture/overview.md` |
| Projections, reporting data lineage, valuation layers | `docs/architecture/reporting-pipeline.md` |
| Reconciliation and economic-truth checks | `docs/quality/reconciliation.md` |
| Tests and database-test strategy | `docs/development/testing.md` |
| Dev Container / local environment | `docs/development/dev-container.md` |
| Isolated agent worktrees and validation | `docs/development/agent-workflow.md` |
| Ghostfolio compatibility | `docs/integrations/ghostfolio.md` |
| Future work | `ROADMAP.md` |
| Completed work | `CHANGELOG.md` |

Package-local `README.md` files are authoritative for the code immediately around them.

## Source-of-truth order

- Source code, configuration, and Flyway migrations describe what the current build actually executes.
- `docs/domain/` defines intended financial/domain semantics. A code mismatch is a defect or an explicit contract change.
- `docs/architecture/` describes stable system boundaries and data flow.
- `docs/development/` describes reproducible engineering procedures.
- `docs/quality/` describes validation contracts.
- `README.md` is the concise product/operator entry point.
- `ROADMAP.md` contains future work only; `CHANGELOG.md` contains completed work.
- `docs/archive/` is historical only. Do not use it as current project context unless asked to investigate history.

## Build defaults

- Java 25+ and Maven.
- Common checks: `mvn test`, `mvn clean verify`, `mvn spotless:check`.
- Local application: `mvn spring-boot:run`.
- Do not record exact test counts or dependency versions here when they can be read from the build.

## Documentation discipline

- One fact, one canonical home. Link instead of copying.
- Update the relevant canonical document when a domain contract or stable architecture boundary changes.
- Update this file only when agent workflow or documentation routing changes.
