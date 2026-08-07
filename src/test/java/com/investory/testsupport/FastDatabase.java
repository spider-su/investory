package com.investory.testsupport;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class FastDatabase {

  private static final String SNAPSHOT = "db/snapshot/schema.sql";
  private static final String REFERENCE_DATA = "db/snapshot/reference-data.sql";
  private static final String MIGRATION_PATTERN = "classpath*:sql/migration/*.sql";

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
      List<String> migrations = migrationResources();
      for (int index = 0; index < migrations.size(); index++) {
        executeResource(
            postgres, migrations.get(index), "/tmp/investory-migration-" + index + ".sql");
      }
    }

    if (resourceExists(REFERENCE_DATA)) {
      executeResource(postgres, REFERENCE_DATA, "/tmp/investory-reference-data.sql");
    }

    return postgres;
  }

  private static List<String> migrationResources() {
    try {
      List<String> migrations =
          Arrays.stream(new PathMatchingResourcePatternResolver().getResources(MIGRATION_PATTERN))
              .map(resource -> resource.getFilename())
              .filter(Objects::nonNull)
              .distinct()
              .sorted()
              .map(fileName -> "sql/migration/" + fileName)
              .toList();
      if (migrations.isEmpty()) {
        throw new IllegalStateException("No Flyway migrations found for " + MIGRATION_PATTERN);
      }
      return migrations;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Cannot discover Flyway migrations for fast database initialization", exception);
    }
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
