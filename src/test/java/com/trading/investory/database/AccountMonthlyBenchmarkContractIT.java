package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.investory.testsupport.TestDatabaseFixtures;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Enforces benchmark correctness against the migrated PostgreSQL database, not only the text of
 * {@code scripts/sql/rebuild_account_monthly_benchmark.sql}. The production contract is that
 * {@code account_monthly_benchmark} is a thin projection of {@code account_monthly_mv} and never
 * recalculates profit from boundary equity independently.
 */
class AccountMonthlyBenchmarkContractIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_benchmark_test")
          .withUsername("investory")
          .withPassword("investory");

  @BeforeAll
  static void migrateDatabase() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:sql/migration")
        .load()
        .migrate();
    TestDatabaseFixtures.loadPersonalBootstrap(POSTGRES);
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Test
  void benchmarkIsAPlainViewDelegatingToCanonicalMonthlyProjection() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {

      // It must be a plain view (relkind 'v'), not an independent materialized calculation.
      try (ResultSet result =
          statement.executeQuery(
              "SELECT c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                  + "WHERE n.nspname = 'investory' AND c.relname = 'account_monthly_benchmark'")) {
        assertTrue(result.next(), "account_monthly_benchmark must exist");
        assertEquals("v", result.getString(1), "benchmark must be a plain view");
      }

      String definition;
      try (ResultSet result =
          statement.executeQuery(
              "SELECT pg_get_viewdef('investory.account_monthly_benchmark'::regclass, true)")) {
        assertTrue(result.next());
        definition = result.getString(1);
      }

      // Delegation to the canonical monthly projection.
      assertTrue(
          definition.contains("account_monthly_mv"),
          "benchmark must read from account_monthly_mv");
      assertTrue(
          definition.contains("total_profit"),
          "benchmark must project canonical total_profit");
      assertTrue(
          definition.contains("compounded_monthly_return"),
          "benchmark must project canonical compounded_monthly_return");

      // No independent boundary-equity profit recalculation.
      assertFalse(
          definition.contains("closing_equity -"),
          "benchmark must not recompute profit from boundary equity");
      assertFalse(
          definition.contains("- opening_equity"),
          "benchmark must not recompute profit from boundary equity");
    }
  }

  @Test
  void benchmarkColumnsMatchCanonicalMonthlyColumns() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema = 'investory' "
                    + "AND table_name = 'account_monthly_benchmark' "
                    + "AND column_name IN ('total_profit', 'compounded_monthly_return', "
                    + "'opening_equity', 'closing_equity')")) {
      assertTrue(result.next());
      assertEquals(
          4L,
          result.getLong(1),
          "benchmark must expose canonical monthly performance columns");
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}

