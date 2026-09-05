package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Small final-migration smoke test for repaired reference data and identity semantics. */
class MigrationDataRepairIT {

  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("migration_data_repair");

  @BeforeAll
  static void migrateEmptyDatabase() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void preservesRepairedReferenceData() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          11,
          MigrationTestDatabase.singleInt(statement, "SELECT count(*) FROM investory.asset_types"));
      assertEquals(
          "EUR",
          singleString(
              statement, "SELECT currency FROM investory.assets WHERE symbol = 'JGPI.DE'"));
      assertEquals(
          "EUR",
          singleString(
              statement,
              "SELECT price_currency FROM investory.asset_price_history "
                  + "WHERE asset_id = (SELECT id FROM investory.assets WHERE symbol = 'JGPI.DE') "
                  + "AND source = 'MANUAL' AND source_symbol = 'jgpi.de' "
                  + "AND price_date = DATE '2025-01-01'"));
      assertEquals(
          "USD",
          singleString(
              statement, "SELECT currency FROM investory.assets WHERE symbol = 'NCLR.UK'"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.asset_price_history aph "
                  + "JOIN investory.assets a ON a.id = aph.asset_id "
                  + "WHERE a.symbol = 'NCLR.UK' AND aph.price_currency <> 'USD'"));
      assertTrue(
          MigrationTestDatabase.singleInt(
                  statement,
                  "SELECT count(*) FROM investory.asset_price_history aph "
                      + "JOIN investory.assets a ON a.id = aph.asset_id "
                      + "WHERE a.symbol = 'JGPI.DE' AND aph.price_origin = 'MANUAL_WEEKLY'")
              > 0);
    }
  }

  private static String singleString(Statement statement, String sql) throws Exception {
    try (var result = statement.executeQuery(sql)) {
      assertTrue(result.next(), "Expected one row for query: " + sql);
      return result.getString(1);
    }
  }
}
