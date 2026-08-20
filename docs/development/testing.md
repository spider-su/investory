# Testing

Investory uses two PostgreSQL initialization paths. Keep them separate because they verify different risks.

## Default tests

Run the existing suite with:

```bash
./mvnw test
```

Architecture dependency rules live in `app/src/test/java/com/smartbox/investory/architecture/LayerDependencyTest.java`.
They protect the one-way business-module boundaries, restrict Retirement to Investment and Long-Term
public contracts, keep shared contracts independent, and prevent Web UI access to domain
infrastructure or repositories. `SimulationPlanService` is the only documented simulation-package
persistence orchestration exception. `InvestmentProfileFacade` reads classification through the
Investment public API; `PlanningPresentation` is Retirement-owned and is not a cross-module
architecture exception.

Tests that do not need a database should remain plain unit tests or Spring test slices.

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

- starts one shared PostgreSQL 17 container for the test JVM;
- activates the `test-fast` profile;
- disables Flyway;
- loads the committed snapshot;
- supplies the container JDBC properties to Spring.

Do not use this base class for tests whose purpose is migration validation.

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

Keep scenario-specific data in focused test fixtures and load it with `@Sql`. Committed
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

Create or update:

```text
~/.testcontainers.properties
```

with:

```properties
testcontainers.reuse.enable=true
```

Reuse is a local optimization only. CI should always use clean containers and databases.
