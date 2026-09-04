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
- Schema changes go through versioned Flyway SQL under `app/src/main/resources/sql/migration`.
- For local runs, use the datasource settings from the current configuration/environment; do not assume a Spring profile changes them.
- For live database debugging, always use the JDBC driver and connection settings from the local profile; do not use `psql` or guessed database credentials.
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
| Maven module structure, dependency rules, and UI/controller placement | `docs/architecture/modularization.md` |
| Dashboard facade, view models, and Thymeleaf flow | `docs/architecture/dashboard-application.md` |
| Projections, reporting data lineage, valuation layers | `docs/architecture/reporting-pipeline.md` |
| Reconciliation and economic-truth checks | `docs/quality/reconciliation.md` |
| Tests and database-test strategy | `docs/development/testing.md` |
| Dev Container / local environment | `docs/development/dev-container.md` |
| Isolated agent worktrees and validation | `docs/development/agent-workflow.md` |
| Future work | `ROADMAP.md` |
| Completed work | `CHANGELOG.md` |

Package-local `README.md` files are authoritative for the code immediately around them.

## Source-of-truth order

- Source code, configuration, and Flyway migrations describe what the current build actually executes.
- `docs/domain/` defines intended financial/domain semantics. A code mismatch is a defect or an explicit contract change.
- `docs/architecture/` describes stable system boundaries and data flow, including `docs/architecture/modularization.md` for Maven module dependencies and where feature code, adapters, and tests belong.
- `docs/development/` describes reproducible engineering procedures.
- `docs/quality/` describes validation contracts.
- `README.md` is the concise product/operator entry point.
- `ROADMAP.md` contains future work only; `CHANGELOG.md` contains completed work.
- `docs/archive/` is historical only. Do not use it as current project context unless asked to investigate history.

## Build defaults

- Java 25+ and Maven.
- In managed/cloud workspaces, always keep both Maven wrapper and dependency caches inside the
  checkout: set `MAVEN_USER_HOME=$PWD/.m2` and add
  `-Dmaven.repo.local=$PWD/.m2/repository` to every Maven command.
- Common checks: `./mvnw test`, `./mvnw clean verify`, `./mvnw spotless:check`.
- Use `./mvnw spotless:apply` only when formatting is needed; keep the formatting check in the normal
  verification path.
- In PowerShell, use `./mvnw.cmd` and always quote Maven `-D...` properties, for example `./mvnw.cmd "-Dit.test=SystemAuditContractIT,BaselineReadinessContractIT" verify`, so shell expansion cannot drop the property.
- Use a default 360-second command timeout for Maven builds and tests; increase it for known long integration suites.
- Local application: `./mvnw -pl app -am spring-boot:run` (`./mvnw.cmd` in PowerShell).
- Docker/Testcontainers in this workspace use `DOCKER_HOST=tcp://127.0.0.1:2375`; set `$env:DOCKER_HOST` before Docker-backed tests or tools.
- Fast database tests use the committed snapshot at `test-support/src/main/resources/db/snapshot/schema.sql` with Flyway disabled; migration tests start from an empty database with Flyway enabled.
- UI smoke and golden-path browser tests need a Chromium install plus `PLAYWRIGHT_BROWSERS_PATH`; the canonical browser-test suite lives under `app` and writes artifacts to `app/target/ui-test-results`.
- `./mvnw clean verify` is the full before-merge check when both the snapshot path and the migration path need to be exercised.
- Do not record exact test counts or dependency versions here when they can be read from the build.

## Documentation discipline

- One fact, one canonical home. Link instead of copying.
- Update the relevant canonical document when a domain contract or stable architecture boundary changes.
- Update this file only when agent workflow or documentation routing changes.
