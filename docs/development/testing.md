# Testing

Investory uses two PostgreSQL initialization paths. Keep them separate because they verify different risks.

## Default tests

Run the existing suite with:

```bash
mvn test
```

Tests that do not need a database should remain plain unit tests or Spring test slices.

## Fast database integration tests

Fast database tests use PostgreSQL Testcontainers but disable Flyway. The database is initialized directly from:

```text
src/test/resources/db/snapshot/schema.sql
```

When the snapshot has not been generated yet, the test support dynamically discovers all `sql/migration/*.sql` classpath resources, sorts them by filename, and applies them directly with `psql`. This keeps the fallback aligned when new migrations are added; generating the snapshot still removes that repeated migration work.

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
- loads the committed snapshot when present;
- loads optional deterministic reference data;
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
3. applies all SQL files from `src/main/resources/sql/migration` in filename order;
4. dumps the clean `investory` schema and migration seed data;
5. writes `src/test/resources/db/snapshot/schema.sql`;
6. deletes the temporary database.

The script never dumps the normal `investory` development database, so personal portfolio rows are not copied into the repository.

Regenerate and commit the snapshot whenever a migration changes:

```bash
bash scripts/update-test-db-snapshot.sh
git add src/test/resources/db/snapshot/schema.sql
git commit -m "Update fast test database snapshot"
```

Review the generated SQL before committing it.

## Reference and fixture data

Shared non-sensitive reference rows can be placed in:

```text
src/test/resources/db/snapshot/reference-data.sql
```

Keep scenario-specific data in focused test fixtures and load it with `@Sql`. Do not put broker exports, personal positions, account identifiers, API keys, or production credentials into test resources.

## Full migration verification

Migration tests must continue to start from an empty PostgreSQL database with Flyway enabled. They verify that production schema history still works and catch drift between migrations and the generated snapshot.

Run full verification before opening or merging a pull request:

```bash
mvn clean verify
```

The required balance is:

```text
Fast feature tests: snapshot + Flyway disabled
Migration tests: empty database + Flyway enabled
CI before merge: both paths
```

A snapshot must never replace production Flyway migrations.

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
