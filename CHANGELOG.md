# Changelog

Completed project work is recorded here. [`ROADMAP.md`](ROADMAP.md) contains future work only.

## 2026-08-20

### Maven modular monolith

- Restructured Investory as a Maven reactor with separate Shared, Investment, Long-Term,
  Retirement, Integrations, Test Support, Web UI, and executable App modules.
- Kept Investment and Long-Term independent, routed Retirement through their public read contracts,
  and enforced business/UI boundaries with architecture tests.
- Moved server-rendered controllers, templates, static resources, and shared UI presentation into
  `adapters/web-ui`; kept application composition, Flyway, and runtime configuration in `app`.
- Aligned architecture, development, testing, reconciliation, and documentation-routing references
  with the current module ownership and paths.

## 2026-08-17

### Reconciliation report semantics

- Added typed C0-C7 checkpoint and issue statuses with explicit `NOT_CHECKED` partial coverage.
- Preserved original failures while marking later downstream issues `BLOCKED`.
- Switched checkpoint counts to uncapped aggregate queries and added stable account diagnostic codes.
- Updated the reconciliation page to distinguish incomplete coverage from a reconciled pipeline.
- Added the reusable ordered check engine and connected current database evidence for C0, C1, C2,
  and C5; C6 and C7 remain explicitly `NOT_CHECKED`.

## 2026-08-14

### Database, persistence, and reconciliation baseline

- Established the PostgreSQL/Testcontainers-safe squashed database baseline through `V01.008`.
- Aligned numeric persistence, deterministic asset identity/source-symbol mappings, explicit currency
  roles, price-history provenance metadata, and the FX usable-status contract.
- Separated production reporting SQL from independent reconciliation reconstruction and persisted
  audit diagnostics.
- Added settlement, account-day, monthly, and account-statistics reconciliation with explicit
  semantic review classifications and `REVIEW` versus `UNRECONCILED` quality semantics.
- Retained immutable `import_source_files` and `import_source_rows` evidence with canonical-row
  provenance links and deletion protections for raw financial facts.
- Added the deterministic clean-PostgreSQL `GoldenRebuild` path and CI golden job.

### Long-term assets, planning, and simulation

- Added manual long-term asset support for real estate, contractual bonds/deposits, planning-only cash
  reserve, and other assets; manual assets contribute to the read-only `InvestmentProfile`.
- Added deterministic retirement simulation with independent inflation, rental-income growth, spending
  growth, asset returns, required/actual/unfunded funding values, and maturity/interest-treatment rules.
- Added Reserve + equity harvest funding strategy with configurable safe reserve, gain-only harvest,
  emergency equity policy, and deterministic manual-cash withdrawal priority.
- Added planning display/input currency presentation (PLN default, USD, EUR) while retaining canonical
  planning storage.
- Added the Actual/Live/Projected planning timeline, planning-year snapshots and baselines, explicit
  close/reopen workflow, current-year bridge, and stable saved-plan calendar/age anchor.

### Documentation

- Reframed the product documentation around accounting, manual long-term assets, deterministic planning,
  and the planning timeline. Added canonical planning-timeline semantics and removed stale simulation
  wording from reconciliation documentation.

## 2026-08-07

### Documentation architecture

- Replaced the short-lived sibling `AGENTS.backend.md` / `AGENTS.sql.md` / `AGENTS.frontend.md`
  split with a concise `AGENTS.md` protocol and task-to-document router.
- Split canonical documentation by concern: domain contracts, architecture, development, quality,
  and integrations.
- Made portfolio accounting, asset/money semantics, and FX normalization explicit domain contracts.
- Replaced the investigation-style pipeline plan with a current reconciliation specification.
- Moved the superseded scoped-agent files and historical pipeline/test-refactor notes under
  `docs/archive/`; routing and ignore files keep that archive out of normal agent context.
- Removed unused Claude and GitHub Copilot agent overlays; generic coding-agent workflow rules now
  live only in `AGENTS.md`.

### Correctness and safety alignment

- Made broker asset resolution fail closed: imports now require one existing canonical asset mapping
  instead of creating or guessing asset identities.
- Made production database URL, username, and password explicit required environment settings.
- Made reconciliation C0 fail when any supplied IBKR or XTB source file is missing or incomplete.
- Changed fast-test migration fallback to discover all migration resources dynamically.
- Added a repository-root default for the Dev Container workspace mount.
- Synchronized local-run, reconciliation, and Ghostfolio compatibility documentation with the current
  runtime behavior.

## Completed before the current roadmap baseline

### Platform and configuration

- Upgraded the application to Java 25, Spring Boot 4.1.0, and Spring Cloud 2025.1.2.
- Moved Lombok version management to the Spring Boot BOM.
- Added a production profile that requires explicit `APP_SECURITY_*` credentials.
- Removed the unused Google OAuth configuration.

### Portfolio and application structure

- Extracted `TaxCalculator` and `CashFlowAggregator` from `PortfolioService` and added focused tests.
- Moved dashboard JavaScript from the HTML template to `static/js/dashboard.js`.
- Tightened Telegram broker-file detection for IBKR imports.

### Build and dependency hygiene

- Configured Spotless with `spotless:apply` and `spotless:check`; lifecycle binding and a pre-commit
  hook remain roadmap work.
- Pinned vulnerable Telegram transitive dependencies in Maven dependency management.

### Documentation

- The initial documentation model used `AGENTS.md` as the canonical engineering source.
- `CLAUDE.md` and `.github/copilot-instructions.md` were kept as thin tool-specific overlays.
