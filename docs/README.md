# Documentation

Use this index to load only the context needed for a task. Do not treat every document as a peer
source of truth.

## Root documents

- [`../README.md`](../README.md): product scope, supported behavior, limitations, and quick setup.
- [`../AGENTS.md`](../AGENTS.md): concise agent working rules and context router.
- [`../ROADMAP.md`](../ROADMAP.md): future work and current priorities only.
- [`../CHANGELOG.md`](../CHANGELOG.md): completed work and documentation history.

## Business overview

- [`business-epics.md`](business-epics.md): short map of the main business-logic epics and their features.

## Domain contracts

These define intended financial semantics. A code mismatch should be investigated, not silently
resolved by changing the documentation.

- [`domain/portfolio-accounting.md`](domain/portfolio-accounting.md): portfolio value, flows, earnings,
  ROI, benchmark, tax, and reconciliation semantics.
- [`domain/asset-identity-and-money.md`](domain/asset-identity-and-money.md): canonical asset identity,
  currency roles, and signed position quantity.
- [`domain/fx-normalization.md`](domain/fx-normalization.md): FX direction, resolution, stale/missing
  rates, and fail-closed reporting behavior.
- [`domain/broker-imports.md`](domain/broker-imports.md): shared import provenance, identity,
  idempotency, lifecycle, numeric, and fail-closed semantics for supported brokers.
- [`domain/long-term-assets.md`](domain/long-term-assets.md): manual real estate, bonds, deposits, cash
  reserves, lifecycle, rental contracts, valuation periods, and planning-facing semantics.
- [`domain/retirement-simulation.md`](domain/retirement-simulation.md): authoritative deterministic
  retirement model, planning buckets, funding/refill strategy, lifecycle, and growth-rate semantics.
- [`domain/planning-timeline.md`](domain/planning-timeline.md): Actual/Live/Projected lifecycle,
  expected-versus-actual baseline, temporal anchor, display currency, and planning/accounting isolation.
- [`domain/retirement-analysis.md`](domain/retirement-analysis.md): deterministic Analysis execution
  context, scenarios, sensitivities, sustainable spending, retirement-age flexibility, and Base
  reconciliation semantics.

## Architecture

- [`architecture/overview.md`](architecture/overview.md): stable application boundaries and major
  components.
- [`architecture/modularization.md`](architecture/modularization.md): Maven-module boundary rules and
  dependency direction for the modular monolith.
- [`architecture/security.md`](architecture/security.md): authentication, roles, route exposure,
  CSRF/session behavior, secret handling, and the current single-owner isolation boundary.
- [`architecture/dashboard-application.md`](architecture/dashboard-application.md): dashboard facade,
  query flow, immutable view models, and server-rendered UI boundary.
- [`architecture/reporting-pipeline.md`](architecture/reporting-pipeline.md): raw ledger ->
  `account_daily` -> reporting layers -> UI/adapters.

## Integrations

- [`integrations/overview.md`](integrations/overview.md): authority and failure boundaries for market,
  FX, Yahoo, Telegram, and optional OpenAI adapters.

## Development and operations

- [`development/testing.md`](development/testing.md): unit/integration/migration testing and database
  snapshot strategy.
- [`development/dev-container.md`](development/dev-container.md): reproducible local development
  environment.
- [`development/agent-workflow.md`](development/agent-workflow.md): isolated worktree/container
  workflow for coding agents.
- [`development/production.md`](development/production.md): production configuration, startup,
  migrations, release verification, restart/recovery, and deployment boundaries.

## Quality

- [`quality/reconciliation.md`](quality/reconciliation.md): C0-C7 pipeline checkpoints,
  economic-truth validation, regression classes, and current reconciliation tooling.
- [`quality/01-investment-dashboard-manual-qa.md`](quality/01-investment-dashboard-manual-qa.md):
  dashboard, import, valuation, and responsive UI manual checks.
- [`quality/02-long-term-assets-manual-qa.md`](quality/02-long-term-assets-manual-qa.md):
  long-term asset, rental contract, lifecycle, and tax manual checks.
- [`quality/03-investment-profile-manual-qa.md`](quality/03-investment-profile-manual-qa.md):
  whole-wealth profile and source-lineage manual checks.
- [`quality/04-retirement-planning-simulation-manual-qa.md`](quality/04-retirement-planning-simulation-manual-qa.md):
  retirement planning and simulation manual checks.
- [`quality/05-integrations-manual-qa.md`](quality/05-integrations-manual-qa.md):
  integration settings, provider, secret, and failure-boundary manual checks.
- [`quality/06-reconciliation-data-quality-manual-qa.md`](quality/06-reconciliation-data-quality-manual-qa.md):
  reconciliation and persisted data-quality manual checks.
- [`reconciliation/local-profile-db-persistence-freeze-readiness.md`](reconciliation/local-profile-db-persistence-freeze-readiness.md): current database/persistence freeze-readiness audit and required remaining checks.

## Operational and investigation material

These documents are useful for repeatable administration or historical diagnosis, but they do not
override the canonical domain, architecture, integration, or quality contracts above.

- [`long-term-assets-bootstrap.md`](long-term-assets-bootstrap.md): developer/admin bootstrap procedure
  for manual long-term assets.
- [`reconciliation/`](reconciliation/): local-profile reports and investigations. Read its
  [`README.md`](reconciliation/README.md) before using an individual report as evidence.
- [`simulation/`](simulation/): simulation support notes and local investigations. Read its
  [`README.md`](simulation/README.md) before treating a document as current behavior.

## Nearest-code documentation

Package-local `README.md` files describe code-specific conventions and should remain next to that
code. In particular:

- `test-support/src/main/java/com/smartbox/investory/testsupport/README.md`: shared PostgreSQL test
  infrastructure, deterministic portfolio fixtures, and scenario-building rules.

## Archive

`archive/` contains historical snapshots and investigation notes retained for comparison during the
documentation reorganization. It is not current project context, is listed in the repository AI ignore
file, and should not be loaded unless historical investigation is requested.
Git history remains the long-term history source.

## Documentation rules

- One fact has one canonical home; other documents link to it.
- Product summary belongs in root `README.md`.
- Intended financial semantics belong in `domain/`.
- Stable data flow, component, and security boundaries belong in `architecture/`.
- External-adapter authority and failure boundaries belong in `integrations/`.
- Repeatable engineering and production procedures belong in `development/`.
- Validation contracts belong in `quality/`.
- Operational and investigation documents may preserve evidence but must link back to the canonical
  contract they validate and must not redefine it.
- Future work belongs in `ROADMAP.md`; completed work belongs in `CHANGELOG.md`.
- Exact implementation details should be read from source/configuration/Flyway instead of copied into
  multiple documents.
