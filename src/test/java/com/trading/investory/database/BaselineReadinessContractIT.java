package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class BaselineReadinessContractIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_baseline_test")
          .withUsername("investory")
          .withPassword("investory");

  @BeforeAll
  static void migrateEmptyDatabase() {
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
  void emptyMigrationProducesFinalBaselineContract() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(6, singleInt(statement, "SELECT count(*) FROM investory.flyway_schema_history"));
      for (String table :
          new String[] {
            "app_users",
            "portfolios",
            "accounts",
            "assets",
            "exchange_rates",
            "asset_source_symbols",
            "asset_price_history"
          }) {
        assertEquals(0, singleInt(statement, "SELECT count(*) FROM investory." + table), table);
      }

      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM information_schema.columns "
                  + "WHERE table_schema = 'investory' AND table_name = 'accounts' "
                  + "AND column_name = 'external_account_id' AND is_nullable = 'NO'"));
      assertTrue(
          exists(
              statement,
              "SELECT 1 FROM pg_constraint WHERE conname = 'ux_accounts_provider_external_account'"));
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM pg_constraint c "
                  + "JOIN pg_class t ON t.oid = c.conrelid "
                  + "WHERE t.relname IN ('accounts', 'cash_operations', 'positions') "
                  + "AND c.confdeltype = 'c'"));
      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM pg_constraint c "
                  + "JOIN pg_class t ON t.oid = c.conrelid "
                  + "WHERE t.relname = 'account_daily' AND c.confdeltype = 'c'"));

      for (String relation :
          new String[] {
            "materialized_view_refresh_history",
            "reporting_materialized_view_dependencies",
            "reporting_materialized_view_refresh_status",
            "portfolio_daily_mv",
            "account_monthly",
            "portfolio_monthly",
            "portfolio_daily"
          }) {
        assertFalse(relationExists(statement, relation), relation);
      }
      assertTrue(relationExists(statement, "app_v_account_monthly"));
      assertTrue(relationExists(statement, "recon_v_account_daily"));

      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('OK')"));
      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('ESTIMATED')"));
      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('SAME_CURRENCY')"));
      assertFalse(singleBoolean(statement, "SELECT investory.fx_status_usable('STALE')"));
      assertFalse(singleBoolean(statement, "SELECT investory.fx_status_usable('MISSING_RATE')"));

      statement.execute("SELECT investory.refresh_reporting_views()");
      statement.execute("SELECT investory.refresh_reconciliation_views()");
      assertTrue(relationExists(statement, "account_monthly_mv"));
      assertTrue(relationExists(statement, "reporting_trade_settlement_reconciliation"));

      String benchmarkDefinition =
          singleString(
              statement,
              "SELECT pg_get_viewdef('investory.account_monthly_benchmark'::regclass, true)");
      assertTrue(benchmarkDefinition.contains("account_monthly_mv"));
      assertFalse(benchmarkDefinition.contains("closing_equity - opening_equity"));
    }
  }

  @Test
  void resolverUsesNearestHistoricalBracketAndNeutralPrecedence() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DELETE FROM investory.exchange_rates");
      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2025-01-01', 'USD', 'EUR', 1, 'TEST', 'HISTORICAL_MONTHLY'), "
              + "(DATE '2025-01-10', 'USD', 'EUR', 2, 'TEST', 'HISTORICAL_MONTHLY'), "
              + "(DATE '2025-01-20', 'USD', 'EUR', 4, 'TEST', 'HISTORICAL_MONTHLY'), "
              + "(DATE '2025-01-30', 'USD', 'EUR', 8, 'TEST', 'HISTORICAL_MONTHLY')");

      assertEquals(
          "3.00000000|ESTIMATED",
          resolverValue(statement, "2025-01-15", "USD", "EUR"));
      assertEquals(
          "0.33333333|ESTIMATED",
          resolverValue(statement, "2025-01-15", "EUR", "USD"));

      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2026-08-05', 'USD', 'PLN', 4, 'TEST', 'MARKET_DAILY'), "
              + "(DATE '2026-08-10', 'USD', 'PLN', 5, 'TEST', 'IBKR_DAILY_REFERENCE'), "
              + "(DATE '2026-08-10', 'EUR', 'PLN', 99, 'TEST', 'IBKR_EXECUTION')");
      assertEquals("5.00000000|OK", resolverValue(statement, "2026-08-10", "USD", "PLN"));

      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2026-08-10', 'USD', 'PLN', 6, 'TEST', 'MARKET_DAILY')");
      assertEquals("6.00000000|OK", resolverValue(statement, "2026-08-10", "USD", "PLN"));

      assertEquals("0.25000000|STALE", resolverValue(statement, "2026-08-10", "PLN", "USD"));
      assertEquals(
          "null|MISSING_RATE", resolverValue(statement, "2026-08-10", "EUR", "USD"));
    }
  }

  @Test
  void rawLedgerDeletionIsRestrictedButDerivedRowsCanBeRemoved() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.app_users(id, username, display_name) "
              + "VALUES (-800001, 'baseline-test', 'Baseline Test')");
      statement.execute(
          "INSERT INTO investory.portfolios(id, name, base_currency, user_id) "
              + "VALUES (-800001, 'Baseline Test', 'USD', -800001)");
      statement.execute(
          "INSERT INTO investory.accounts "
              + "(id, external_account_id, currency, provider, name, owner, portfolio_id) "
              + "VALUES (-800001, 'baseline-account', 'USD', 'XTB', 'Baseline Test', 'Test', -800001)");
      statement.execute(
          "INSERT INTO investory.cash_operations "
              + "(id, account_id, operation, amount, currency, date) "
              + "VALUES (-800001, -800001, 'DEPOSIT', 10, 'USD', now())");

      assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM investory.accounts WHERE id = -800001"));
      assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM investory.portfolios WHERE id = -800001"));

      statement.execute(
          "INSERT INTO investory.account_daily "
              + "(account_id, snapshot_date, valuation_currency) "
              + "VALUES (-800001, DATE '2026-08-10', 'USD')");
      statement.execute("DELETE FROM investory.account_daily WHERE account_id = -800001");
      statement.execute("DELETE FROM investory.cash_operations WHERE id = -800001");
      statement.execute("DELETE FROM investory.accounts WHERE id = -800001");
      statement.execute("DELETE FROM investory.portfolios WHERE id = -800001");
      statement.execute("DELETE FROM investory.app_users WHERE id = -800001");
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static String resolverValue(
      Statement statement, String date, String sourceCurrency, String targetCurrency)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT round(fx_rate_to_target, 8), conversion_status FROM investory.resolve_fx_rate("
                + "DATE '"
                + date
                + "', '"
                + sourceCurrency
                + "', '"
                + targetCurrency
                + "')")) {
      assertTrue(result.next());
      String rate = result.getBigDecimal(1) == null ? "null" : result.getBigDecimal(1).toPlainString();
      return String.format("%s|%s", rate, result.getString(9));
    }
  }

  private static int singleInt(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      return result.getInt(1);
    }
  }

  private static boolean singleBoolean(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      return result.getBoolean(1);
    }
  }

  private static String singleString(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next());
      String value = result.getString(1);
      assertNotNull(value);
      return value;
    }
  }

  private static boolean exists(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      return result.next();
    }
  }

  private static boolean relationExists(Statement statement, String relation) throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT to_regclass('investory." + relation.replace("'", "''") + "') IS NOT NULL")) {
      assertTrue(result.next());
      return result.getBoolean(1);
    }
  }
}
