# Testing

Investory uses two PostgreSQL initialization paths. Keep them separate because they verify different risks.

## Default tests

Run the existing suite with:

```bash
./mvnw test
```

JaCoCo instrumentation is configured in the root Maven build. `verify` writes an HTML report for
each code-bearing module under `target/site/jacoco/index.html`; the app module also writes the
cross-module report under `app/target/site/jacoco-aggregate/`. CI combines unit, app-hosted
cross-module, and integration-test execution data before enforcing the aggregate baseline in
`docs/quality/coverage-baseline.json`. Generated DTOs and intentional boundary adapters must not
be padded with assertion-free tests merely to raise a total.

Architecture dependency rules live in `LayerDependencyTest`. They protect the one-way boundary
between accounting/reporting, profile/dashboard application code, planning, and deterministic
simulation. Persistence-owning orchestration adapters live under infrastructure packages; the
deterministic simulation package remains persistence-free.

Tests that do not need a database should remain plain unit tests or Spring test slices.

Pure tests belong with the production module when their dependencies permit it. A small set of
investment tests remains app-hosted because it uses the shared portfolio fixture module, which
currently depends on investment; moving those tests without first splitting that fixture module
would create a Maven cycle. App-bound web configuration and browser/template tests remain in app.

Use this ownership split for new tests:

- `modules/<feature>/src/test/java`: feature-owned logic, feature-owned REST adapters, and
  module-scoped integration tests.
- `integrations/src/test/java`: provider/plugin adapter tests.
- `app/src/test/java`: composition wiring, security/configuration wiring, template/browser UI,
  architecture checks, and cross-module database/reporting contracts.
- `test-support/src/main/java/com/smartbox/investory/testsupport`: shared deterministic fixtures
  and reusable database-test infrastructure only.

The current explicit ownership map and temporary app-hosted exceptions are maintained in
`docs/development/test-ownership.md`.

## Fast database integration tests

Fast database tests use PostgreSQL Testcontainers but disable Flyway. The database is initialized directly from:

```text
test-support/src/main/resources/db/snapshot/schema.sql
```

The snapshot is required. If it is missing, fast-test support fails with instructions to regenerate it;
it never executes migrations itself.

Create a Spring integration test by extending `FastDatabaseTest`:

```java
@SpringBootTest
class PortfolioRepositoryTest extends FastDatabaseTest {
    // tests
}
```

The base class:

- starts one shared PostgreSQL 18.6 container for the test JVM;
- activates the `test-fast` profile;
- disables Flyway;
- loads the committed snapshot;
- supplies the container JDBC properties to Spring.

Do not use this base class for tests whose purpose is migration validation.

## Read-only system contracts

The fast read-only layer starts the real Spring application against the committed database
snapshot. It uses one canonical persisted HappyInvestor story and independent expected facts from
`test-support`; tests must not derive expected values from a REST response or rendered page.
`ProfilePersistedFactsIT` also covers empty, brokerage-only, Long-Term-only, and mixed portfolio
shapes, plus repeated-read source immutability.

Financial expectations are owned by the canonical HappyInvestor story and existing domain/reporting
contracts. Keep Income Base, investment result, TWR, XIRR, deposits, withdrawals, net worth, income,
allocation, cash, and invested capital semantics in those contracts rather than repeating simplified
definitions in test documentation. External deposits and withdrawals must be handled according to
the reporting/domain contract. Deterministic story facts and contract-derived values are separate
from live market-price and FX observations; reconcile a discrepancy against both the story fact and
authoritative contract before calling it a product defect.

- Feature-owned REST controller tests verify stable API fields without recreating a generic
  cross-module response contract. `ProfilePersistedFactsIT` verifies the complete Profile
  composition against persisted HappyInvestor facts.
- `HappyInvestorReadOnlyUiIT` starts the same application and snapshot with Chromium. Its tests are
  grouped by page/module and verify the financial values users see. It performs navigation only;
  writes, refreshes, imports, exports, and form submissions belong to action integration tests.
  Dashboard and portfolio monetary summaries use the shared compact display contract: values show
  stable `K`/`M` suffixes with the same precision as Long-Term Assets; prices, rates, percentages,
  and form inputs keep their own exact display rules.
- `HappyInvestorOverlayIdempotencyIT` proves that reapplying the canonical overlay restores all
  mutable columns and is idempotent.

REST, UI, and provider/action integration suites run in separate CI matrix jobs. Their databases
are isolated, so the layers can execute in parallel. UI uses the real REST/application composition;
mock-fed view-model tests remain unit or web-slice tests and do not replace this system contract.

## Browser UI smoke tests

`UiPageSmokeIT` starts the application on a random port, loads the canonical snapshot-backed
PostgreSQL database, and visits every rendered page with headless Chromium. It checks the response,
title, stable page text, browser errors, and failed first-party requests. Focused checks exercise
small navigation and JavaScript controls without duplicating canonical financial-value assertions.
Failed page cases write a screenshot, rendered HTML, and Playwright trace under
`app/target/ui-test-results`.

`LongTermAssetCrudUiIT` covers a canonical real-estate browser flow through the rendered
list/detail/create/edit pages, with PostgreSQL assertions for persisted property facts and rental
term editing. It also covers cash-reserve create/edit/archive/reactivate through the rendered UI,
including PostgreSQL assertions for the value and archive state. It does not provide complete
browser CRUD coverage for bonds or personal assets. `LongTermAssetLifecyclePostgresIT` covers the
real-estate/rental command, JPA,
PostgreSQL, economics, historical snapshot, archive, and reactivate lifecycle without duplicating
browser assertions. Do not treat either test as a complete browser lifecycle test for every
Long-Term type.

`InvestmentDashboardGoldenUiIT` rebuilds the investment portfolio from the committed IBKR, XTB,
and FX golden-path fixtures in an isolated PostgreSQL database. It checks every rendered dashboard
amount against the canonical golden-story checkpoints and existing reporting contracts (with view
models/database state used as reconciliation evidence). It then drives
the market and currency refresh controls with deterministic mocks: `VWRA.UK` becomes `150.00`, and
USD/PLN becomes `4.00` with reciprocal PLN/USD `0.25`. After each refresh it verifies the persisted
price/rates, recalculated balances and positions, and all visible overview, performance, income,
allocation, account, currency, winner, and loser values. Import/export UI is intentionally excluded.

Install the matching Chromium binary once, then run the suite:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = "$PWD/.playwright"
./mvnw.cmd -pl app -am -DskipTests install
./mvnw.cmd -pl app exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1"
$env:DOCKER_HOST = "tcp://127.0.0.1:2375"
./mvnw.cmd -pl app test-compile failsafe:integration-test failsafe:verify "-Dit.test=UiPageSmokeIT,HappyInvestorReadOnlyUiIT,LongTermAssetCrudUiIT,PlanSimulationCrudUiIT,InvestmentDashboardGoldenUiIT"
```

CI compiles, tests, and installs the reactor once in the unit job, then publishes the packaged
reactor outputs. Backend and UI matrix legs consume those outputs and do not independently rebuild
the reactor. Chromium is restored from a versioned cache when available, installed once by the UI
browser setup job, and published for the UI matrix legs. Browser setup and UI execution are separate
jobs, so their failures remain distinct; UI failure artifacts include screenshots, HTML, and traces.
The Maven/Java setup itself is defined in `.github/actions/setup-java-maven`.

The application composition job runs `ApplicationContextIT` in parallel with Unit and Docs, before
the backend, schema, golden-path, and UI fan-out. It starts the complete Spring application against
the fast snapshot database and has no assertions beyond context startup, so missing beans,
incompatible module interfaces, and configuration failures fail early.

Backend integration tests run as independent matrix legs with `fail-fast: false`:

- `contracts`: valuation input and stale-valuation refresh, system-audit, reporting,
  baseline-readiness, HappyInvestor read-only REST, overlay idempotency, and benchmark contracts;
- `imports`: PostgreSQL FX update plus IBKR/XTB import contracts;
- `reconciliation-notifications-longterm`: remaining app-owned database contracts;
- `investment-rest`: Investment REST boundary integration tests;
- `retirement-rest`: Retirement REST boundary integration tests.

Every committed `*IT` class belongs to exactly one backend, UI, or golden-path CI allowlist. When a
new integration test is added, add it to the owning leg in the same change; Maven naming alone does
not make the split CI execute it.

The schema migration job remains isolated because the test cleans and reapplies the complete Flyway
chain. It fingerprints the migration files, schema snapshot, schema checkpoint test, and Flyway
configuration. A successful result is cached under that exact fingerprint; an unchanged fingerprint
skips the expensive job. A cache is written only after a green test, and a cache miss always runs
against a disposable database.

The migration layer stays deliberately small:

- `FlywayMigrationChainIT` proves the complete chain applies to an empty database.
- `SchemaStructureContractIT` checks the application-facing tables, views, and key columns.
- `MigrationDataRepairIT` checks the repaired reference-data semantics from the final migration.
- `LongTermHardeningMigrationIT` proves annual rental-tax-base facts remain unchanged and verifies
  database chronology and lifecycle-provenance constraints.

Before the production freeze, existing versioned migrations may be cleaned up or consolidated while
establishing the production baseline. After that baseline is applied to persistent production
databases, versioned migrations are immutable; every later schema change must use a new additive
migration.

Financially critical calculation packages have additional JaCoCo thresholds in
`docs/quality/coverage-baseline.json`. The existing aggregate 70% line / 50% branch gate remains;
the extra gate covers investment performance, projection, valuation, Long-Term application
services, and retirement planning packages, plus changed executable lines in those packages.
Long-Term's critical application package requires 80% line and 65% branch coverage from the
combined module and app-owned integration execution data. It is a targeted protection layer, not a
reason to add broad end-to-end tests.

## Generate the schema snapshot

Run from the repository root with Docker available:

```bash
bash scripts/update-test-db-snapshot.sh
```

The script:

1. starts the PostgreSQL service from `.devcontainer/compose.yml`;
2. creates a temporary `investory_snapshot` database;
3. applies all SQL files from `app/src/main/resources/sql/migration` in filename order;
4. dumps the clean `investory` schema and migration seed data;
5. writes `test-support/src/main/resources/db/snapshot/schema.sql`;
6. deletes the temporary database.

The script never dumps the normal `investory` development database, so personal portfolio rows are not copied into the repository.

Regenerate and commit the snapshot whenever a migration changes:

```bash
bash scripts/update-test-db-snapshot.sh
git add test-support/src/main/resources/db/snapshot/schema.sql
git commit -m "Update fast test database snapshot"
```

Review the generated SQL before committing it.

## Fixture data

Keep scenario-specific data in focused test fixtures and load it with `@Sql` only when it is not
part of the canonical snapshot. Committed
broker-derived fixtures are allowed only when they are reduced, deterministic, anonymized, free of
credentials and identity-bearing personal data, and limited to regression semantics. Account IDs may
remain where a regression fixture requires them.

## Full migration verification

Migration tests must continue to start from an empty PostgreSQL database with Flyway enabled. They verify that production schema history still works and catch drift between migrations and the generated snapshot.

The Flyway baseline contains anonymized sample portfolio configuration plus public/reference market data. Integration tests execute the same migration chain as production. Tests requiring an empty business dataset explicitly remove the necessary sample data after migration instead of skipping migrations.

Run full verification before opening or merging a pull request:

```bash
./mvnw clean verify
```

The required balance is:

```text
Fast feature tests: snapshot + Flyway disabled
Migration tests: empty database + Flyway enabled
CI before merge: both paths
```

A snapshot must never replace production Flyway migrations.

## Private archive pre-release check

The public golden job uses only the reduced corpus. Before a release, run the same clean-database
rebuild against the full private broker archive, outside public CI, with the archive directory and
source-file hash manifest recorded by the release operator. The archive must be treated as input
evidence, not committed test data. Any changed hash or unexplained reconciliation difference makes
the private check `NOT_READY` until reviewed.

The existing `tools/ReconRunner.java` can be used for the source-file C0/C1 pass:

```text
ReconRunner <private-archive-directory> <jdbc-url> <user> <password>
```

Run it only against a clean local release-validation database after the normal import and reporting
rebuild. Keep the full archive and credentials outside the repository and public CI artifacts.

## Local Testcontainers reuse

Container startup can dominate a short test run. Local reuse is optional.

Integration tests use one PostgreSQL 18.6 container per test JVM. `FastDatabase` creates the worker
database from the container's administrative `postgres` database and points Spring at that
database; scoped migration and golden tests use additional isolated databases in the same
container. Names are `it_<run-id>_<worker-id>[_scope]`, using `GITHUB_RUN_ID` when available.
The Maven property `it.test.workers` (default `1`) is passed to the infrastructure; the related
system properties `investory.test.run-id`, `investory.test.worker-id`,
`investory.test.database.prefix`, `investory.test.postgres.image`, and
`investory.test.database.cleanup` are available for CI/local overrides. Cleanup terminates active
connections before dropping databases and stops the container once at JVM shutdown.

Create or update:

```text
~/.testcontainers.properties
```

with:

```properties
testcontainers.reuse.enable=true
```

Reuse is a local optimization only. CI should always use clean containers and databases.
