package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Cheap proof that the complete Flyway chain applies to an empty PostgreSQL database. */
class FlywayMigrationChainIT {

  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("flyway_migration_chain");

  @BeforeAll
  static void migrateEmptyDatabase() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void appliesEveryMigrationSuccessfully() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          MigrationTestDatabase.migrationScriptCount(),
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.flyway_schema_history "
                  + "WHERE success AND version IS NOT NULL"));
    }
  }
}
