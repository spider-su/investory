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
import com.investory.testsupport.FastDatabase;
import org.junit.jupiter.api.Test;

class MaterializedViewRefreshContractIT {

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

  @Test
  void explicitProductionRefreshLeavesAllExpectedMvsQueryable() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SELECT investory.refresh_reporting_views()");
      statement.execute("SELECT investory.refresh_app_views()");
      for (String applicationView :
          Set.of(
              "app_v_current_asset_price",
              "app_v_normalized_cash_operations",
              "app_v_account_statistics",
              "app_v_portfolio_kpi_summary")) {
        assertTrue(relationExists(statement, applicationView), applicationView);
      }

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
      statement.execute("SELECT investory.refresh_recon_views()");
      for (String reconciliationView :
          Set.of("recon_v_fx", "recon_v_account_daily", "recon_v_realized_result")) {
        assertTrue(relationExists(statement, reconciliationView), reconciliationView);
      }
      for (String materializedView : RECONCILIATION_MVS) {
        assertTrue(relationExists(statement, materializedView));
      }
    }
  }

  @Test
  void applicationPresentationRoundingIsSeparateFromReconciliationDecisions() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT investory.application_display_value(123.456), "
                    + "investory.reconciliation_display_value(123.456), "
                    + "investory.reconciliation_values_match(100, 100.049), "
                    + "investory.reconciliation_values_match(100, 100.051)")) {
      assertTrue(result.next());
      assertEquals("123.46", result.getBigDecimal(1).toPlainString());
      assertEquals("123", result.getBigDecimal(2).toPlainString());
      assertTrue(result.getBoolean(3));
      assertFalse(result.getBoolean(4));
    }

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      for (String applicationView :
          Set.of(
              "app_v_account_monthly",
              "app_v_account_monthly_benchmark",
              "app_v_portfolio_daily",
              "app_v_portfolio_monthly",
              "app_v_account_statistics",
              "app_v_portfolio_kpi_summary")) {
        assertTrue(relationExists(statement, applicationView), applicationView);
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT pg_get_viewdef('investory.app_v_account_statistics'::regclass, true), "
                  + "pg_get_viewdef('investory.app_v_portfolio_monthly'::regclass, true)")) {
        assertTrue(result.next());
        assertTrue(result.getString(1).contains("application_display_value"));
        assertTrue(result.getString(2).contains("application_display_value"));
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
    return DriverManager.getConnection(
        FastDatabase.container().getJdbcUrl(),
        FastDatabase.container().getUsername(),
        FastDatabase.container().getPassword());
  }
}
