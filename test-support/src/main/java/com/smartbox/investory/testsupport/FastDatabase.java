package com.smartbox.investory.testsupport;

import java.io.IOException;
import java.net.URL;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class FastDatabase {

  private static final String SNAPSHOT = "db/snapshot/schema.sql";

  private static final WorkerDatabase DATABASE = startDatabase();

  private FastDatabase() {}

  public static PostgreSQLContainer<?> container() {
    return SharedPostgres.container();
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

  /** Returns a separately initialized snapshot-backed database for a stateful test scope. */
  public static WorkerDatabase scopedDatabase(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("A non-blank database scope is required");
    }
    return startDatabase(scope);
  }

  private static WorkerDatabase startDatabase() {
    return startDatabase(null);
  }

  private static WorkerDatabase startDatabase(String scope) {
    WorkerDatabase database = SharedPostgres.database(scope);

    if (!resourceExists(SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database snapshot "
              + SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    if (!snapshotLoaded(database)) executeResource(database, SNAPSHOT, "/tmp/investory-schema.sql");

    return database;
  }

  private static boolean resourceExists(String resource) {
    URL url = FastDatabase.class.getClassLoader().getResource(resource);
    return url != null;
  }

  private static void executeResource(
      WorkerDatabase database, String resource, String containerPath) {
    try {
      PostgreSQLContainer<?> postgres = SharedPostgres.container();
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

  private static boolean snapshotLoaded(WorkerDatabase database) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT to_regclass('investory.flyway_schema_history')")) {
      return result.next() && result.getString(1) != null;
    } catch (Exception ignored) {
      return false;
    }
  }
}
