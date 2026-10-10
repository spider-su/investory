package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies the final benchmark schema generates distinct identities on fresh installations. */
class BenchmarkSequenceMigrationIT {

  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("benchmark_sequence");

  @BeforeAll
  static void migrateFinalSchema() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void insertsBenchmarkRowsWithDistinctGeneratedIds() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.benchmark_monthly_closes "
              + "(symbol, month, close_price, fetched_at) "
              + "VALUES ('SPY', DATE '2026-01-01', 600, now()), "
              + "('SPY', DATE '2026-02-01', 610, now())");
      assertEquals(
          2,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(DISTINCT id) FROM investory.benchmark_monthly_closes WHERE symbol = 'SPY'"));
    }
  }
}
