package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Preserves annual tax-base facts and enforces Long-Term chronology and lifecycle provenance. */
class LongTermHardeningMigrationIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("long_term_hardening");

  @BeforeAll
  static void migrateExistingAnnualTaxBases() throws Exception {
    MigrationTestDatabase.assertDisposable(DATABASE);
    MigrationTestDatabase.flyway(DATABASE).clean();
    MigrationTestDatabase.migrateTo(DATABASE, "01.008");
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO investory.real_estate
              (id, portfolio_id, name, currency, value, tax_base, archived_at)
          VALUES (9491, 1, 'Annual taxable property', 'PLN', 400000, 3200, NULL),
                 (9492, 1, 'Unspecified base', 'PLN', 100000, NULL, NULL),
                 (9493, 1, 'Zero base', 'PLN', 100000, 0, NULL),
                 (9494, 1, 'Archived annual base', 'PLN', 100000, 3000, DATE '2025-12-31'),
                 (9495, 1, 'Fractional annual base', 'PLN', 100000, 100.125, NULL);
          """);
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9491 AND tax_base = 3200"));
      assertTrue(columnComment(statement).startsWith("Annual rental-tax base in asset currency."));
    }
    MigrationTestDatabase.migrateTo(DATABASE, "01.008");
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void preservesExistingAnnualBasesAndTheirContract() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9491 AND tax_base = 3200"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9495 AND tax_base = 100.125"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9491 AND tax_base = 3200 AND tax_base * 0.085 = 272"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9494 AND tax_base = 3000 AND archived_at = DATE '2025-12-31'"));
      assertEquals(
          "Annual rental-tax base in asset currency. Annual rental tax = tax_base * 0.085; NULL means unspecified.",
          columnComment(statement));
    }
  }

  @Test
  void rerunningFlywayDoesNotRewriteAnnualBases() throws Exception {
    MigrationTestDatabase.migrateTo(DATABASE, "01.008");
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT tax_base FROM investory.real_estate WHERE id = 9491")) {
      assertTrue(result.next());
      assertEquals(0, new BigDecimal("3200").compareTo(result.getBigDecimal(1)));
    }
  }

  @Test
  void rejectsMaturityBeforeAcquisitionAndAllowsEqualOrUnknownDates() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute(
            """
            INSERT INTO investory.bond
                (id, portfolio_id, name, currency, value, acquisition_date, interest_rate, maturity_date)
            VALUES (9496, 1, 'Same-day bond', 'PLN', 100, DATE '2025-01-01', 0.04, DATE '2025-01-01'),
                   (9497, 1, 'Unknown acquisition', 'PLN', 100, NULL, 0.04, DATE '2025-01-01');
            INSERT INTO investory.cash_reserve
                (id, portfolio_id, name, currency, value, acquisition_date, maturity_date)
            VALUES (9498, 1, 'Same-day cash', 'PLN', 100, DATE '2025-01-01', DATE '2025-01-01'),
                   (9499, 1, 'Plain cash', 'PLN', 100, DATE '2025-01-01', NULL),
                   (9500, 1, 'Unknown acquisition', 'PLN', 100, NULL, DATE '2025-01-01');
            """);
        assertCheckViolation(
            connection,
            statement,
            "UPDATE investory.bond SET maturity_date = DATE '2024-12-31' WHERE id = 9496",
            "ck_bond_maturity_after_acquisition");
        assertCheckViolation(
            connection,
            statement,
            "UPDATE investory.cash_reserve SET maturity_date = DATE '2024-12-31' WHERE id = 9498",
            "ck_cash_reserve_maturity_after_acquisition");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void rejectsTerminationAfterExpectedEndAndAllowsEqualOrOpenEnds() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute(
            """
            INSERT INTO investory.rental_contract
                (id, real_estate_id, start_date, end_date, terminated_date)
            VALUES (9591, 9491, DATE '2025-01-01', DATE '2025-01-31', DATE '2025-01-31'),
                   (9592, 9492, DATE '2025-01-01', NULL, DATE '2025-01-31'),
                   (9593, 9493, DATE '2025-01-01', DATE '2025-01-31', NULL);
            """);
        assertCheckViolation(
            connection,
            statement,
            "UPDATE investory.rental_contract SET terminated_date = DATE '2025-02-01' WHERE id = 9591",
            "ck_rental_contract_termination_before_expected_end");
        assertCheckViolation(
            connection,
            statement,
            "UPDATE investory.rental_contract SET end_date = DATE '2025-01-30' WHERE id = 9591",
            "ck_rental_contract_termination_before_expected_end");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void rejectsOverlappingEffectivePeriodsAtDatabaseBoundary() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute(
            "INSERT INTO investory.rental_contract (id, real_estate_id, start_date, end_date) "
                + "VALUES (9594, 9491, DATE '2026-01-01', DATE '2026-01-31'), "
                + "(9595, 9491, DATE '2026-02-01', DATE '2026-02-28')");
        SQLException overlap =
            assertThrows(
                SQLException.class,
                () ->
                    statement.execute(
                        "INSERT INTO investory.rental_contract "
                            + "(id, real_estate_id, start_date, end_date) VALUES "
                            + "(9596, 9491, DATE '2026-01-31', DATE '2026-02-02')"));
        assertEquals("23P01", overlap.getSQLState());
        assertTrue(
            overlap.getMessage().contains("ex_rental_contract_non_overlapping_effective_periods"),
            overlap.getMessage());
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void addsEmptyLifecycleProvenanceWithoutClaimingLegacyCompleteness() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.long_term_asset_history"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.long_term_asset_archive_interval"));
    }
  }

  @Test
  void enforcesLifecycleTypeChronologyAndOneOpenInterval() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute(
            """
            INSERT INTO investory.long_term_asset_history (asset_id, asset_type, complete)
            VALUES (9491, 'REAL_ESTATE', true);
            INSERT INTO investory.long_term_asset_archive_interval
                (asset_id, archived_from, archived_to)
            VALUES (9491, DATE '2025-01-01', DATE '2025-01-01'),
                   (9491, DATE '2026-01-01', NULL);
            """);
        assertCheckViolation(
            connection,
            statement,
            "INSERT INTO investory.long_term_asset_history (asset_id, asset_type) VALUES (9492, 'DEPOSIT')",
            "ck_long_term_asset_history_type");
        assertCheckViolation(
            connection,
            statement,
            "INSERT INTO investory.long_term_asset_archive_interval (asset_id, archived_from, archived_to) VALUES (9491, DATE '2025-02-01', DATE '2025-01-31')",
            "ck_long_term_asset_archive_interval_dates");
        SQLException duplicateOpen =
            assertThrows(
                SQLException.class,
                () ->
                    statement.execute(
                        "INSERT INTO investory.long_term_asset_archive_interval (asset_id, archived_from) VALUES (9491, DATE '2027-01-01')"));
        assertEquals("23505", duplicateOpen.getSQLState());
        assertTrue(
            duplicateOpen.getMessage().contains("uq_long_term_asset_archive_interval_open"),
            duplicateOpen.getMessage());
      } finally {
        connection.rollback();
      }
    }
  }

  private static void assertCheckViolation(
      Connection connection, Statement statement, String sql, String constraint) throws Exception {
    var savepoint = connection.setSavepoint();
    try {
      SQLException failure = assertThrows(SQLException.class, () -> statement.execute(sql));
      assertEquals("23514", failure.getSQLState());
      assertTrue(failure.getMessage().contains(constraint), failure.getMessage());
    } finally {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    }
  }

  private static String columnComment(Statement statement) throws SQLException {
    try (var result =
        statement.executeQuery(
            """
            SELECT col_description(attrelid, attnum)
            FROM pg_attribute
            WHERE attrelid = 'investory.real_estate'::regclass AND attname = 'tax_base'
            """)) {
      assertTrue(result.next());
      return result.getString(1);
    }
  }
}
