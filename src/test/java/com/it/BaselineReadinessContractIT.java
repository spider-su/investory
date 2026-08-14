package com.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;

@TestMethodOrder(OrderAnnotation.class)
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
        .schemas("investory")
        .defaultSchema("investory")
        .createSchemas(true)
        .locations("classpath:sql/migration")
        .load()
        .migrate();
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Test
  @Order(1)
  void migratedBaselineProducesFinalSchemaAndSampleDataContract() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          migrationScriptCount(),
          singleInt(
              statement,
              "SELECT count(*) FROM investory.flyway_schema_history WHERE success AND version IS NOT NULL"));
      assertEquals(
          "sample.user|Sample User",
          singleString(
              statement,
              "SELECT username || '|' || display_name FROM investory.app_users WHERE id = 1"));
      assertEquals(
          "Sample Portfolio|USD|1|sample.user",
          singleString(
              statement,
              "SELECT p.name || '|' || p.base_currency || '|' || p.user_id || '|' || u.username FROM investory.portfolios p JOIN investory.app_users u ON u.id = p.user_id WHERE p.id = 1"));
      assertEquals(
          11,
          singleInt(statement, "SELECT count(*) FROM investory.accounts WHERE portfolio_id = 1"));
      for (String accountName :
          new String[] {
            "Sample PLN Account",
            "Sample USD Account",
            "Sample EUR Account",
            "Sample Metals Account",
            "Sample Retirement Account",
            "Sample PLN Cash Account",
            "Sample USD Trading Account",
            "Sample EUR Cash Account",
            "Sample Income Account",
            "Sample PLN Reserve Account",
            "Sample IBKR Account"
          }) {
        assertTrue(
            exists(
                statement,
                "SELECT 1 FROM investory.accounts WHERE portfolio_id = 1 AND name = '"
                    + accountName
                    + "'"));
      }
      for (String currency : new String[] {"USD", "EUR", "PLN"})
        assertTrue(
            exists(statement, "SELECT 1 FROM investory.currencies WHERE id = '" + currency + "'"));
      for (String provider : new String[] {"XTB", "IBKR"})
        assertTrue(
            exists(statement, "SELECT 1 FROM investory.providers WHERE id = '" + provider + "'"));
      assertTrue(exists(statement, "SELECT 1 FROM investory.asset_types WHERE id = 'EQUITY'"));
      assertTrue(exists(statement, "SELECT 1 FROM investory.assets WHERE symbol = 'AAPL.US'"));
      for (String table :
          new String[] {"cash_operations", "positions", "import_history", "account_daily"})
        assertEquals(0, singleInt(statement, "SELECT count(*) FROM investory." + table), table);

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
      for (String table : new String[] {"cash_operations", "positions"}) {
        assertEquals(
            1,
            singleInt(
                statement,
                "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema = 'investory' AND table_name = '"
                    + table
                    + "' AND column_name = 'id' AND column_default IS NULL AND is_identity = 'NO'"),
            table + " IDs must be importer-owned");
      }
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
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM pg_depend "
                  + "WHERE objid IN ('investory.mv_reconstructed_position_daily'::regclass, "
                  + "'investory.mv_reconstructed_cash_daily'::regclass) "
                  + "AND refobjid IN ('investory.v_reconstructed_position_daily'::regclass, "
                  + "'investory.v_reconstructed_cash_daily'::regclass)"));

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
  @Order(2)
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

      assertEquals("3.00000000|ESTIMATED", resolverValue(statement, "2025-01-15", "USD", "EUR"));
      assertEquals("0.33333333|ESTIMATED", resolverValue(statement, "2025-01-15", "EUR", "USD"));

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

      assertEquals("0.16666667|OK", resolverValue(statement, "2026-08-10", "PLN", "USD"));
      assertEquals("0.12500000|STALE", resolverValue(statement, "2026-08-10", "EUR", "USD"));
    }
  }

  @Test
  @Order(3)
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

      assertThrows(
          SQLException.class,
          () -> statement.executeUpdate("DELETE FROM investory.accounts WHERE id = -800001"));
      assertThrows(
          SQLException.class,
          () -> statement.executeUpdate("DELETE FROM investory.portfolios WHERE id = -800001"));

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

  @Test
  @Order(4)
  void estimatedCashFxIsIncludedAndKeepsAggregateComplete() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.app_users(id, username, display_name) "
              + "VALUES (-800002, 'estimated-fx-test', 'Estimated FX Test')");
      statement.execute(
          "INSERT INTO investory.portfolios(id, name, base_currency, user_id) "
              + "VALUES (-800002, 'Estimated FX Test', 'USD', -800002)");
      statement.execute(
          "INSERT INTO investory.accounts "
              + "(id, external_account_id, currency, provider, name, owner, portfolio_id) "
              + "VALUES (-800002, 'estimated-fx-account', 'EUR', 'XTB', 'Estimated FX Test', 'Test', -800002)");
      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2025-01-10', 'EUR', 'USD', 2, 'TEST', 'HISTORICAL_MONTHLY'), "
              + "(DATE '2025-01-20', 'EUR', 'USD', 4, 'TEST', 'HISTORICAL_MONTHLY')");
      statement.execute(
          "INSERT INTO investory.cash_operations "
              + "(id, account_id, operation, amount, currency, date) "
              + "VALUES (-800002, -800002, 'DEPOSIT', 10, 'EUR', TIMESTAMP WITH TIME ZONE '2025-01-15 00:00:00+00')");

      assertEquals(
          "30.00000000|ESTIMATED",
          singleString(
              statement,
              "SELECT round(amount_in_portfolio_base_currency, 8)::text || '|' || portfolio_conversion_status "
                  + "FROM investory.normalized_cash_operations WHERE operation_id = -800002"));

      statement.execute("SELECT investory.refresh_reporting_views()");
      assertEquals(
          "30.00000000|0|true",
          singleString(
              statement,
              "SELECT round(converted_cash_subtotal, 8)::text || '|' || missing_fx_count || '|' || is_complete "
                  + "FROM investory.account_statistics WHERE account_id = -800002"));
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
      String rate =
          result.getBigDecimal(1) == null ? "null" : result.getBigDecimal(1).toPlainString();
      return String.format("%s|%s", rate, result.getString(2));
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

  private static int migrationScriptCount() throws SQLException {
    try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "sql", "migration"))) {
      return (int)
          files
              .map(Path::getFileName)
              .map(Path::toString)
              .filter(name -> name.matches("^V\\d+\\.\\d+__.*\\.sql$"))
              .count();
    } catch (java.io.IOException exception) {
      throw new SQLException("Cannot list Flyway migrations", exception);
    }
  }
}
