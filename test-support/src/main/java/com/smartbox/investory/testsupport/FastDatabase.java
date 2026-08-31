package com.smartbox.investory.testsupport;

import java.io.IOException;
import java.net.URL;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class FastDatabase {

  private static final String SNAPSHOT = "db/snapshot/schema.sql";

  private static final PostgreSQLContainer<?> POSTGRES = startDatabase();

  private FastDatabase() {}

  public static PostgreSQLContainer<?> container() {
    return POSTGRES;
  }

  private static PostgreSQLContainer<?> startDatabase() {
    PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-bookworm")
            .withDatabaseName("investory_test")
            .withUsername("investory")
            .withPassword("investory");

    postgres.start();

    if (!resourceExists(SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database snapshot "
              + SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    executeResource(postgres, SNAPSHOT, "/tmp/investory-schema.sql");

    return postgres;
  }

  private static boolean resourceExists(String resource) {
    URL url = FastDatabase.class.getClassLoader().getResource(resource);
    return url != null;
  }

  private static void executeResource(
      PostgreSQLContainer<?> postgres, String resource, String containerPath) {
    try {
      postgres.copyFileToContainer(MountableFile.forClasspathResource(resource), containerPath);
      Container.ExecResult result =
          postgres.execInContainer(
              "psql",
              "-v",
              "ON_ERROR_STOP=1",
              "--username",
              postgres.getUsername(),
              "--dbname",
              postgres.getDatabaseName(),
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
}
