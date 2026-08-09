package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class MaterializedViewRefreshContractTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_mv_test")
          .withUsername("investory")
          .withPassword("investory");

  private static final Set<String> PRODUCTION_MVS =
      Set.of(
          "account_monthly_mv",
          "portfolio_monthly_mv",
          "account_statistics",
          "portfolio_kpi_summary",
          "portfolio_currency_breakdown",
          "portfolio_asset_allocation",
          "symbol_performance");

  private static final Set<String> RECONCILIATION_MVS =
      Set.of(
          "reporting_account_monthly_profit_reconciliation",
          "reporting_account_statistics_vs_daily_reconciliation",
          "reporting_account_daily_cashflow_reconciliation",
          "v_account_daily_reconciliation",
          "reporting_trade_settlement_reconciliation");

  @BeforeAll
  static void migrateDatabase() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:sql/migration")
        .load()
        .migrate();
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Test
  void explicitProductionRefreshLeavesAllExpectedMvsQueryable() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SELECT investory.refresh_reporting_views()");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT matviewname FROM pg_matviews WHERE schemaname = 'investory' "
                  + "AND matviewname NOT IN ("
                  + "'reporting_account_monthly_profit_reconciliation',"
                  + "'reporting_account_statistics_vs_daily_reconciliation',"
                  + "'reporting_account_daily_cashflow_reconciliation',"
                  + "'v_account_daily_reconciliation',"
                  + "'reporting_trade_settlement_reconciliation')")) {
        Set<String> actual = new java.util.HashSet<>();
        while (result.next()) {
          actual.add(result.getString(1));
        }
        assertEquals(PRODUCTION_MVS, actual);
      }

      for (String mv : PRODUCTION_MVS) {
        try (ResultSet result = statement.executeQuery("SELECT 1 FROM investory." + mv + " LIMIT 1")) {
          result.next();
        }
      }

      assertFalse(relationExists(statement, "portfolio_daily_mv"));
      assertFalse(relationExists(statement, "account_monthly"));
      assertFalse(relationExists(statement, "portfolio_monthly"));
      assertFalse(relationExists(statement, "portfolio_daily"));
      assertFalse(relationExists(statement, "v_reporting_daily_fx_rate"));
      assertFalse(relationExists(statement, "v_activity_events"));
      assertFalse(relationExists(statement, "reporting_validation_issue"));
      assertFalse(relationExists(statement, "v_portfolio_data_quality"));
      assertFalse(relationExists(statement, "v_portfolio_data_quality_refresh"));
      assertFalse(relationExists(statement, "reporting_materialized_view_dependencies"));
      assertFalse(relationExists(statement, "reporting_materialized_view_refresh_status"));
      assertFalse(relationExists(statement, "materialized_view_refresh_history"));
    }
  }

  @Test
  void reconciliationRefreshIsSeparateFromProductionRefresh() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SELECT investory.refresh_reporting_views()");
      statement.execute("SELECT investory.refresh_reconciliation_views()");
      for (String materializedView : RECONCILIATION_MVS) {
        assertTrue(relationExists(statement, materializedView));
      }
    }
  }

  @Test
  void sectorAllocationRemainsDeferredWithoutCanonicalTaxonomy() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'investory' "
                    + "AND table_name IN ('sectors', 'asset_sectors', 'sector_allocation')")) {
      assertTrue(result.next());
      assertEquals(0, result.getLong(1));
    }
  }

  private static boolean relationExists(Statement statement, String name) throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'investory' AND c.relname = '" + name + "')")) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
