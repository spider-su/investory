package com.investory.testsupport;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class FastDatabase {

  private static final String SNAPSHOT = "db/snapshot/schema.sql";
  private static final String REFERENCE_DATA = "db/snapshot/reference-data.sql";
  private static final List<String> MIGRATION_FALLBACK =
      List.of(
          "sql/migration/V01.000__Initial_schema.sql",
          "sql/migration/V01.001__Initial_data.sql",
          "sql/migration/V01.002__checks_and_views.sql",
          "sql/migration/V01.003__asset_price_history_import.sql");

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

    if (resourceExists(SNAPSHOT)) {
      executeResource(postgres, SNAPSHOT, "/tmp/investory-schema.sql");
    } else {
      for (int index = 0; index < MIGRATION_FALLBACK.size(); index++) {
        String migration = MIGRATION_FALLBACK.get(index);
        executeResource(postgres, migration, "/tmp/investory-migration-" + index + ".sql");
      }
    }

    if (resourceExists(REFERENCE_DATA)) {
      executeResource(postgres, REFERENCE_DATA, "/tmp/investory-reference-data.sql");
    }

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
