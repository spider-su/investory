# ROADMAP

Investory is in a v1 feature freeze. The product has moved from feature development to
release-candidate hardening for a trusted, single-owner, privately/network-controlled deployment.
Correctness fixes, reconciliation work, release-critical performance fixes, tests, and operational
hardening are in scope. Unrelated refactoring and new product functionality normally wait for v1.1.

Completed work is recorded in [`CHANGELOG.md`](CHANGELOG.md). Current financial contracts remain in
[`docs/domain/`](docs/domain/); architecture and test/release contracts remain in
[`docs/architecture/`](docs/architecture/), [`docs/quality/`](docs/quality/), and
[`docs/development/`](docs/development/).

## Release path

```text
RC1 -> real-data acceptance -> blocker fixes -> RC2 -> private production -> stabilization -> v1.1
```

The release question is simple: can the owner trust Investory's numbers when the database is rebuilt
from the real broker archive?

## P0 — RC correctness

Only correctness or data-integrity work that can block private production belongs here.

### Accounting correctness

- [x] Derive ordinary document periods from authoritative dates and correction periods from the
  correction issue date; do not let the open UI month decide the booked month.
- [x] Derive a missing FX date from the preceding weekday and keep explicit source dates when
  supplied.
- [x] Apply the health contribution band from the month after the threshold is crossed; retain the
  social-contribution deduction exclusion for Fundusz Pracy.
- [ ] Add a second non-UoP reference profile and matrix assertions that report the exercised
  accounting/ZUS branches for every month. The current oracle still has a qualifying-UoP-only
  golden path.
- [ ] Validate the accounting migration/snapshot pair after the in-progress schema consolidation;
  no accounting result is release evidence until both paths load the same contracts.
- [x] Clean replay of the migration chain, fixture loading, dependency-ordered refreshes, and
  regeneration of both snapshots now pass. Application-level integration rerun remains pending
  after the reactor artifacts are rebuilt.
- [x] Harden accounting schema boundaries: month periods, source-profile ownership, source
  immutability, and legacy document amount invariants.
- [ ] Obtain advisor review of the tax-period, ZUS deduction, health-band, correction, and FX
  semantics before treating the accounting POC as filing-grade.

- [ ] Complete the portfolio-scoped dashboard/read-model review. Fix any remaining path where a
  selected portfolio can receive system-wide metrics or stale data from another scope.
- [ ] Complete the portfolio-scoped planning aggregation review. The current market aggregation is
  shared; make the private-release behavior explicit and safe for the supported single-owner scope.
- [ ] Re-check remaining Long-Term database/data invariants against the current migrations and
  persistence tests. Add an item here only if a concrete invariant defect remains.
- [ ] Make golden and private-archive reconciliation reliable and reviewable: clean rebuild,
  source-file hash manifest, archive completeness evidence, and a report that separates execution
  status from archive-coverage status.
- [ ] Resolve any unexplained material reconciliation residual or financial-calculation defect
  discovered during real-data acceptance. Do not widen tolerances to make a report pass.

P0 exit criterion: a clean full private archive rebuild produces a reviewed reconciliation report;
important totals agree with broker/source evidence; the import -> accounting -> `account_daily` ->
reporting -> dashboard chain is explained; Long-Term and Retirement/planning values have been
manually reviewed; and no material residual remains unexplained.

## P1 — RC reliability

These items are release work only when they affect correctness, reproducibility, or material operator
risk. General cleanup is not a release blocker.

- [ ] Preserve and improve refresh failure/rollback coverage, including the explicit refresh order and
  behavior after a failed materialized-view stage.
- [ ] Measure and reduce the remaining expensive refreshes only when exact outputs remain unchanged.
  The tracked 2026-09-03 populated baseline includes approximately `90.8 s` for
  `app_v_normalized_daily_price_mv`, `54.3 s` for `app_v_normalized_cash_operations`, `53.2 s` for
  `recon_v_reconstructed_cash_daily_mv`, `23.2 s` for
  `recon_v_reconstructed_position_daily_mv`, and `17.9 s` for
  `recon_v_account_daily_reconciliation_mv`. Re-measure on the representative profile; do not
  optimize from planner cost alone.
- [ ] Keep the CI release evidence dependable: unit/integration suites, `GoldenRebuildIT`, browser
  golden tests, formatting/docs checks, and the schema-migration job. Review the
  `SchemaMigrationCheckpoint2IT` setup cost and reduce it only without weakening disposable-database
  isolation.
- [ ] Review remaining broad Investment reads and null/compatibility APIs only where they create a
  demonstrated scope, correctness, or material performance risk before release.
- [ ] Validate the production configuration, startup/restart behavior, health endpoint, logging
  needed for diagnosis, and backup/restore procedure in a production-like environment.

## Deferred accounting cleanup

These items do not block the correctness pass and should follow the reference-matrix work:

- [ ] Consolidate invoice/expense concepts and remove the redundant VAT satellite only after the
  canonical reporting contract is documented and migrated safely.
- [ ] Remove profile-1 compatibility overloads from `AccountingPocRepository`; compiler-driven
  cleanup must preserve profile scoping.
- [ ] Split the repository into acquisition, canonical facts, filing, and reference-query services.
- [ ] Encrypt or externalize retained source payloads and document the retention/redaction policy.
- [ ] Add obligation/tax-input acquisition to the real source-to-promotion E2E path.
- [ ] Add the `IssueKind`/`Resolution` review model and month-page UX after tax semantics are signed
  off.

## P2 — Production operations and stabilization

These are useful immediately after deployment, but do not block a trusted private release unless
real-data acceptance proves otherwise.

- [ ] Add refresh status, retry, stale-report visibility, and operator-facing reconciliation
  notifications.
- [ ] Add Prometheus/Micrometer metrics, correlation IDs (including import-batch correlation), and
  production logging controls.
- [ ] Improve operational notification delivery, recovery, replay, and mute controls where needed for
  safe day-to-day ownership.
- [ ] Add the production runbook details that are still missing: backup cadence/retention, restore
  rehearsal, freshness checks, and restart triage.

### Stabilization phase

After private deployment, freeze major features and monitor the real system before starting v1.1:

- monitor imports and reconciliation, including stale or failed refreshes;
- inspect production logs and verify scheduled market/FX refreshes;
- verify notifications and operator recovery paths;
- compare planning and dashboard values with expected values after real imports;
- fix production defects and record them before resuming product development.

## P3 — v1.1+ product evolution

These are deliberately outside the private-production critical path:

- production-grade multi-profile Accounting: scope canonical facts, staging/source evidence, filing
  state, uniqueness constraints and repositories by profile, with isolation tests; the current
  Accounting POC intentionally remains single-profile and rejects other profiles;
- notification UI, replay/mute UX, weekly digest, and additional notification rules;
- positions workspace, richer asset detail, profit-copy cleanup, optional enrichment, and mobile UX;
- import progress SSE, additional broker parsers, stronger partial-overlap reporting, and import
  validation reports;
- IBKR FlexQuery scheduled pulls and NAV/account-summary ingestion;
- richer planning history, assumption calibration, explicit real-estate sale strategy, and Monte
  Carlo/sequence-risk modeling.

## Release gates

### RC1 gate

- build green;
- unit and integration tests green;
- golden reconciliation green;
- browser/golden UI tests green;
- no known P0 correctness defect.

The repository already contains dedicated CI jobs for these checks. A source review alone is not a
green run; retain the actual CI evidence.

### Real-data acceptance gate

- clean database;
- full private broker archive imported and rebuilt;
- reconciliation report reviewed;
- important totals checked against broker/source evidence;
- import -> accounting -> `account_daily` -> reporting -> dashboard verified;
- Long-Term and Retirement/planning results manually reviewed;
- no unexplained material reconciliation residuals.

### RC2 / private-production gate

- all discovered P0 defects resolved;
- critical P1 reliability problems resolved;
- production configuration validated with explicit non-development credentials;
- database backup and restore tested;
- `/actuator/health` verified;
- production logging is sufficient for diagnosis;
- deployment and recovery runbook verified.

After this gate, deploy to the trusted private environment and begin stabilization.

## Security boundary

The v1 deployment assumption is one trusted owner behind a private/network-controlled boundary. For
that deployment, require explicit production credentials, read authentication, TLS at the boundary,
privileged write/import routes, protected database access, and tested backups. Authentication defaults
must not be the development `change-me-*` values.

Do not make full multi-user isolation a private-production P0 blocker: the current application is
explicitly a personal/single-owner system. Before exposing one instance to mutually untrusted users,
complete and verify per-user data scoping across financial tables, UI CSRF protection, and import rate
limiting. Those are public/multi-user requirements and remain outside this private-release path unless
the deployment assumption changes.

The Accounting POC currently runs with CSRF protection disabled to keep the POC UI and write flows
usable. Re-enable and verify CSRF protection after the Accounting POC phase is complete, before any
deployment outside the trusted private/network-controlled boundary.

## Explicitly deferred

- database-schema/module isolation experiments unless concrete release evidence requires them;
- replacing JPA with R2DBC/WebFlux;
- real-time market-data websockets;
- migrating away from Telegram;
- promoting Yahoo to the primary quote source;
- broad dependency or architectural cleanup without a demonstrated release benefit;
- any new product functionality not listed in the release gates or P2 stabilization work.

## Roadmap discipline

Keep this file short. A task belongs here only if it answers one of four questions: what blocks
private production, what happens immediately after deployment, what is v1.1 product work, or what is
intentionally deferred. When work ships, remove it and record it in [`CHANGELOG.md`](CHANGELOG.md).
Update canonical domain/architecture/quality documents only when a release decision changes their
contract.
