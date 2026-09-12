package com.smartbox.investory.accounting.testsupport;

import java.io.IOException;
import java.net.URL;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class AccountingDatabase {

  private static final String SNAPSHOT = "db/snapshot/schema.sql";
  private static final String POC_SNAPSHOT = "db/snapshot/poc.sql";

  private static final com.smartbox.investory.testsupport.WorkerDatabase DATABASE = startDatabase();

  private AccountingDatabase() {}

  public static PostgreSQLContainer<?> container() {
    return com.smartbox.investory.testsupport.SharedPostgres.container();
  }

  public static String jdbcUrl() {
    return DATABASE.jdbcUrl();
  }

  public static String username() {
    return DATABASE.username();
  }

  public static String password() {
    return DATABASE.password();
  }

  public static String pocJdbcUrl() {
    return PocDatabaseHolder.INSTANCE.jdbcUrl();
  }

  public static String pocUsername() {
    return PocDatabaseHolder.INSTANCE.username();
  }

  public static String pocPassword() {
    return PocDatabaseHolder.INSTANCE.password();
  }

  /** Returns a separately initialized snapshot-backed database for a stateful test scope. */
  public static com.smartbox.investory.testsupport.WorkerDatabase scopedDatabase(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("A non-blank database scope is required");
    }
    return startDatabase(scope);
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startDatabase() {
    return startDatabase(null);
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startDatabase(String scope) {
    com.smartbox.investory.testsupport.WorkerDatabase database =
        com.smartbox.investory.testsupport.SharedPostgres.database(
            scope == null || scope.isBlank() ? "accounting" : "accounting_" + scope);

    loadSnapshot(database, false);
    return database;
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startPocDatabase() {
    com.smartbox.investory.testsupport.WorkerDatabase database =
        com.smartbox.investory.testsupport.SharedPostgres.database("accounting_poc");

    loadSnapshot(database, true);
    // Keep the fast fixture self-healing when an older packaged snapshot is present on the test
    // classpath.
    ensureZusFixture(database);
    return database;
  }

  private static void ensureZusFixture(com.smartbox.investory.testsupport.WorkerDatabase database) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.accounting_source_evidence (
              id BIGSERIAL PRIMARY KEY,
              source_type VARCHAR(16) NOT NULL,
              external_reference VARCHAR(256) NOT NULL,
              original_filename VARCHAR(512), content_type VARCHAR(128),
              received_at TIMESTAMPTZ NOT NULL, document_date DATE,
              content_hash BYTEA NOT NULL, payload BYTEA NOT NULL,
              processing_status VARCHAR(32) NOT NULL, processing_error VARCHAR(1000),
              UNIQUE (source_type, external_reference)
          )
          """);
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS source_id BIGINT");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS source_id BIGINT");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.accounting_poc_profile (
              id SMALLINT PRIMARY KEY,
              has_uop BOOLEAN NOT NULL,
              CONSTRAINT chk_accounting_poc_profile_singleton CHECK (id = 1)
          )
          """);
      statement.executeUpdate(
          "INSERT INTO investory.accounting_poc_profile (id, has_uop) VALUES (1, TRUE) "
              + "ON CONFLICT (id) DO NOTHING");
      statement.executeUpdate(
          """
          INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
          SELECT period, 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900,
                 '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'
            FROM generate_series(DATE '2026-01-01', DATE '2026-08-01', INTERVAL '1 month') period
           WHERE NOT EXISTS (
                 SELECT 1
                   FROM investory.accounting_poc_tax_input input
                  WHERE input.tax_period = period
                    AND input.input_type = 'JDG_COMPULSORY_SOCIAL_ZUS')
          """);
    } catch (java.sql.SQLException exception) {
      throw new IllegalStateException("Cannot initialize accounting ZUS test fixture", exception);
    }
  }

  private static final class PocDatabaseHolder {
    private static final com.smartbox.investory.testsupport.WorkerDatabase INSTANCE =
        startPocDatabase();
  }

  private static void loadSnapshot(
      com.smartbox.investory.testsupport.WorkerDatabase database, boolean includePoc) {

    if (!resourceExists(SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database snapshot "
              + SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    if (includePoc && !resourceExists(POC_SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database POC snapshot "
              + POC_SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    if (!schemaLoaded(database)) {
      executeResource(database, SNAPSHOT, "/tmp/investory-schema.sql");
    }
    if (includePoc && !pocSnapshotLoaded(database)) {
      executeResource(database, POC_SNAPSHOT, "/tmp/investory-poc.sql");
    }
  }

  private static boolean resourceExists(String resource) {
    URL url = AccountingDatabase.class.getClassLoader().getResource(resource);
    return url != null;
  }

  private static void executeResource(
      com.smartbox.investory.testsupport.WorkerDatabase database,
      String resource,
      String containerPath) {
    try {
      PostgreSQLContainer<?> postgres =
          com.smartbox.investory.testsupport.SharedPostgres.container();
      postgres.copyFileToContainer(MountableFile.forClasspathResource(resource), containerPath);
      Container.ExecResult result =
          postgres.execInContainer(
              "psql",
              "-v",
              "ON_ERROR_STOP=1",
              "--username",
              database.username(),
              "--dbname",
              database.databaseName(),
              "--file",
              containerPath);

      if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Failed to initialize fast test database from "
                + resource
                + ". stdout: "
                + result.getStdout()
                + "; stderr: "
                + result.getStderr());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while initializing fast test database from " + resource, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Cannot initialize fast test database from " + resource, exception);
    }
  }

  private static boolean schemaLoaded(com.smartbox.investory.testsupport.WorkerDatabase database) {
    // The fast snapshot deliberately excludes Flyway's history table.
    return relationExists(database, "investory.currencies");
  }

  private static boolean pocSnapshotLoaded(
      com.smartbox.investory.testsupport.WorkerDatabase database) {
    return relationExists(database, "investory.accounting_poc_profile");
  }

  private static boolean relationExists(
      com.smartbox.investory.testsupport.WorkerDatabase database, String relation) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT to_regclass('" + relation + "')")) {
      return result.next() && result.getString(1) != null;
    } catch (Exception ignored) {
      return false;
    }
  }
}
