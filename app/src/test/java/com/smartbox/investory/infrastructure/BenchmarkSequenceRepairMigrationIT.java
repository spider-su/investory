package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies the benchmark identity sequence is advanced past existing rows. */
class BenchmarkSequenceRepairMigrationIT {

  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("benchmark_sequence_repair");

  @BeforeAll
  static void migrateWithAnOutOfSyncSequence() throws Exception {
    MigrationTestDatabase.assertDisposable(DATABASE);
    MigrationTestDatabase.flyway(DATABASE).clean();
    MigrationTestDatabase.migrateTo(DATABASE, "1.22");
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.benchmark_monthly_closes "
              + "(id, symbol, month, close_price, fetched_at) "
              + "VALUES (4, 'SPY', DATE '2026-01-01', 600, now())");
      statement.execute("SELECT setval('investory.benchmark_monthly_closes_id_seq', 1, true)");
    }
    MigrationTestDatabase.flyway(DATABASE).migrate();
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void nextGeneratedIdIsAboveExistingBenchmarkRows() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          5,
          MigrationTestDatabase.singleInt(
              statement, "SELECT nextval('investory.benchmark_monthly_closes_id_seq')"));
    }
  }
}
