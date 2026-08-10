package com.investory.testsupport;

import java.io.IOException;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

public final class TestDatabaseFixtures {

  private static final String PERSONAL_BOOTSTRAP = "db/personal-bootstrap.sql";

  private TestDatabaseFixtures() {}

  public static void loadPersonalBootstrap(PostgreSQLContainer<?> postgres) {
    try {
      postgres.copyFileToContainer(
          MountableFile.forClasspathResource(PERSONAL_BOOTSTRAP), "/tmp/investory-personal.sql");
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
              "/tmp/investory-personal.sql");
      if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Failed to load personal test fixture: " + result.getStdout() + result.getStderr());
      }
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to load personal test fixture", exception);
    }
  }
}
