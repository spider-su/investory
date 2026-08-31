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
- [`domain/retirement-simulation.md`](domain/retirement-simulation.md): authoritative deterministic
  retirement model, planning buckets, funding/refill strategy, lifecycle, and growth-rate semantics.
- [`domain/planning-timeline.md`](domain/planning-timeline.md): Actual/Live/Projected lifecycle,
  expected-versus-actual baseline, temporal anchor, display currency, and planning/accounting isolation.

## Architecture

- [`architecture/overview.md`](architecture/overview.md): stable application boundaries and major
  components.
- [`architecture/dashboard-application.md`](architecture/dashboard-application.md): dashboard facade,
  query flow, immutable view models, and server-rendered UI boundary.
- [`architecture/reporting-pipeline.md`](architecture/reporting-pipeline.md): raw ledger ->
  `account_daily` -> reporting layers -> UI/adapters.

## Development

- [`development/testing.md`](development/testing.md): unit/integration/migration testing and database
  snapshot strategy.
- [`development/dev-container.md`](development/dev-container.md): reproducible local development
  environment.
- [`development/agent-workflow.md`](development/agent-workflow.md): isolated worktree/container
  workflow for coding agents.

## Quality

- [`quality/reconciliation.md`](quality/reconciliation.md): C0-C7 pipeline checkpoints,
  economic-truth validation, regression classes, and current reconciliation tooling.
- [`reconciliation/local-profile-db-persistence-freeze-readiness.md`](reconciliation/local-profile-db-persistence-freeze-readiness.md): current database/persistence freeze-readiness audit and required remaining checks.

## Nearest-code documentation

Package-local `README.md` files describe code-specific conventions and should remain next to that
code. In particular:

- `src/test/java/com/smartbox/testsupport/portfolio/README.md`: deterministic portfolio test data
  and scenario-building rules.

## Archive

`archive/` contains historical snapshots and investigation notes retained for comparison during the
documentation reorganization. It is not current project context, is listed in the repository AI ignore
file, and should not be loaded unless historical investigation is requested.
Git history remains the long-term history source.

## Documentation rules

- One fact has one canonical home; other documents link to it.
- Product summary belongs in root `README.md`.
- Intended financial semantics belong in `domain/`.
- Stable data flow and component boundaries belong in `architecture/`.
- Repeatable engineering procedures belong in `development/`.
- Validation contracts belong in `quality/`.
- Future work belongs in `ROADMAP.md`; completed work belongs in `CHANGELOG.md`.
- Exact implementation details should be read from source/configuration/Flyway instead of copied into
  multiple documents.
