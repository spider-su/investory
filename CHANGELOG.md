# Changelog

Completed project work is recorded here. [`ROADMAP.md`](ROADMAP.md) contains future work only.

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
