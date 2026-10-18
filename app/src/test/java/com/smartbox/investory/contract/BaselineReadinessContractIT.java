package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.SharedPostgres;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Baseline Readiness Contract")
class BaselineReadinessContractIT {

  private static final WorkerDatabase DATABASE = SharedPostgres.database("baseline");

  private static final Flyway FLYWAY =
      Flyway.configure()
          .dataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password())
          .schemas("investory")
          .defaultSchema("investory")
          .createSchemas(true)
          .locations("classpath:sql/migration")
          .load();

  @BeforeAll
  static void migrateEmptyDatabase() throws SQLException {
    Flyway.configure().configuration(FLYWAY.getConfiguration()).target("01.009").load().migrate();
    try (Connection connection =
            DriverManager.getConnection(
                DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        Statement statement = connection.createStatement()) {
      long assetId = singleInt(statement, "SELECT id FROM investory.assets ORDER BY id LIMIT 1");
      statement.execute(
          "INSERT INTO investory.asset_price_history(asset_id, price_date, source, source_symbol, price_origin, price_currency, close_price, quality_score, quality_class, is_observed, is_proxy) VALUES ("
              + assetId
              + ", CURRENT_DATE - 1, 'MANUAL', 'MIGRATION_TEST', 'MANUAL', 'USD', 80, 25, 'MANUAL_ACCEPTED', true, false), ("
              + assetId
              + ", CURRENT_DATE, 'MANUAL', 'MIGRATION_TEST', 'MANUAL', 'USD', 90, 25, 'MANUAL_ACCEPTED', true, false)");
    }
    FLYWAY.migrate();
  }

  @AfterAll
  static void stopDatabase() {
    DATABASE.close();
  }

  @DisplayName("migrated Baseline Produces Final Schema And Sample Data Contract")
  @Test
  @Order(1)
  void migratedBaselineProducesFinalSchemaAndSampleDataContract() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.flyway_schema_history "
                  + "WHERE success AND version = '01.008' "
                  + "AND script = 'V01.008__long_term_assets.sql'"));
      FLYWAY.validate();
      assertEquals(0, FLYWAY.info().pending().length, "all discovered migrations applied");
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
              "SELECT 1 FROM pg_constraint WHERE conname = 'ux_accounts_portfolio_provider_external_account'"));
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
            "fx_daily_rates",
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
                  + "WHERE objid IN ('investory.recon_v_reconstructed_position_daily_mv'::regclass, "
                  + "'investory.recon_v_reconstructed_cash_daily_mv'::regclass) "
                  + "AND refobjid IN ('investory.app_v_reconstructed_position_daily'::regclass, "
                  + "'investory.app_v_reconstructed_cash_daily'::regclass)"));

      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('OK')"));
      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('ESTIMATED')"));
      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('SAME_CURRENCY')"));
      assertTrue(singleBoolean(statement, "SELECT investory.fx_status_usable('CARRY_FORWARD')"));
      assertFalse(singleBoolean(statement, "SELECT investory.fx_status_usable('STALE')"));
      assertFalse(singleBoolean(statement, "SELECT investory.fx_status_usable('MISSING_RATE')"));

      statement.execute("SELECT investory.refresh_app_views()");
      statement.execute("SELECT investory.refresh_reconstructed_position_daily()");
      statement.execute("SELECT investory.refresh_reconstructed_account_market_daily()");
      statement.execute("SELECT investory.refresh_reconstructed_cash_daily()");
      statement.execute("SELECT investory.refresh_account_daily_reconciliation()");
      statement.execute("SELECT investory.refresh_reconciliation_reporting_views()");
      assertTrue(relationExists(statement, "app_v_account_monthly"));
      assertTrue(relationExists(statement, "recon_v_trade_settlement"));

      String benchmarkDefinition =
          singleString(
              statement,
              "SELECT pg_get_viewdef('investory.app_v_account_monthly_benchmark'::regclass, true)");
      assertTrue(benchmarkDefinition.contains("app_v_account_monthly"));
      assertFalse(benchmarkDefinition.contains("closing_equity - opening_equity"));
    }
  }

  @DisplayName("manual price selection migration raises only forward manual rank")
  @Test
  @Order(2)
  void manualPriceMigrationRaisesOnlyForwardManualRank() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          "25|100",
          singleString(
              statement,
              "SELECT string_agg(quality_score::text, '|' ORDER BY price_date) "
                  + "FROM investory.asset_price_history "
                  + "WHERE source = 'MANUAL' AND source_symbol = 'MIGRATION_TEST' "
                  + "AND price_date IN (CURRENT_DATE - 1, CURRENT_DATE)"));
    }
  }

  @DisplayName("resolver Uses Canonical Daily FX And Never Execution Fallback")
  @Test
  @Order(2)
  void resolverUsesCanonicalDailyFxAndNeverExecutionFallback() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DELETE FROM investory.exchange_rates");
      statement.execute(
          "DELETE FROM investory.exchange_rates WHERE rate_date >= DATE '2025-01-01'");
      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method, source_rate_date) VALUES "
              + "(DATE '2025-01-15', 'USD', 'EUR', 1.25, 'TEST', 'OBSERVED', DATE '2025-01-15'), "
              + "(DATE '2025-01-15', 'EUR', 'USD', 0.80, 'TEST', 'INTERPOLATED', DATE '2025-01-14'), "
              + "(DATE '2025-01-14', 'USD', 'PLN', 4.00, 'TEST', 'OBSERVED', DATE '2025-01-14')");

      assertEquals(
          "1.00000000|SAME_CURRENCY", resolverValue(statement, "2025-01-15", "USD", "USD"));
      assertEquals("1.25000000|OK", resolverValue(statement, "2025-01-15", "USD", "EUR"));
      assertEquals("0.80000000|ESTIMATED", resolverValue(statement, "2025-01-15", "EUR", "USD"));
      assertEquals(
          "4.00000000|CARRY_FORWARD", resolverValue(statement, "2025-01-15", "USD", "PLN"));
      assertEquals(
          "4.00000000|CARRY_FORWARD", resolverValue(statement, "2030-01-15", "USD", "PLN"));
      try (ResultSet result =
          statement.executeQuery(
              "SELECT source_rate_date, age_days, conversion_status, "
                  + "investory.fx_status_usable(conversion_status) AS usable "
                  + "FROM investory.resolve_fx_rate(DATE '2030-01-15', 'USD', 'PLN')")) {
        assertTrue(result.next());
        assertEquals("2025-01-14", result.getDate("source_rate_date").toString());
        assertTrue(result.getInt("age_days") > 4);
        assertEquals("CARRY_FORWARD", result.getString("conversion_status"));
        assertTrue(result.getBoolean("usable"));
      }

      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, purpose, source, method, observed_at) VALUES "
              + "(DATE '2025-01-15', 'EUR', 'PLN', 99, 'EXECUTION', 'TEST', 'XTB_EXECUTION', now())");
      assertEquals("null|MISSING_RATE", resolverValue(statement, "2025-01-15", "EUR", "PLN"));
      assertEquals("null|MISSING_RATE", resolverValue(statement, "2025-01-16", "EUR", "PLN"));

      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, source, method, source_rate_date) VALUES "
              + "(DATE '2025-01-16', 'EUR', 'PLN', 4.50, 'DAILY', 'OBSERVED', DATE '2025-01-16')");
      assertEquals("4.50000000|OK", resolverValue(statement, "2025-01-16", "EUR", "PLN"));
      assertEquals("0.22222222|OK", resolverValue(statement, "2025-01-16", "PLN", "EUR"));
      assertEquals(
          "4.50000000|CARRY_FORWARD",
          singleString(
              statement,
              "SELECT round(fx_rate_to_target, 8) || '|' || conversion_status "
                  + "FROM investory.resolve_transaction_fx_rate("
                  + "TIMESTAMPTZ '2025-01-17 12:00:00+01', 'EUR', 'PLN', 'TRANSACTION')"));
      statement.execute(
          "INSERT INTO investory.exchange_rates "
              + "(rate_date, base, to_currency, rate, purpose, source, method, observed_at) VALUES "
              + "((CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Warsaw')::date, 'USD', 'EUR', 0.90, 'EXECUTION', 'XTB', 'XTB_EXECUTION', now()), "
              + "((CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Warsaw')::date, 'PLN', 'EUR', 4.50, 'EXECUTION', 'IBKR', 'IBKR_EXECUTION', now())");
      assertEquals(
          "0.90000000|OK",
          singleString(
              statement,
              "SELECT round(fx_rate_to_target, 8) || '|' || conversion_status "
                  + "FROM investory.resolve_transaction_fx_rate(now(), 'USD', 'EUR', 'TRANSACTION')"));
      assertEquals(
          "0.22222222|OK",
          singleString(
              statement,
              "SELECT round(fx_rate_to_target, 8) || '|' || conversion_status "
                  + "FROM investory.resolve_transaction_fx_rate(now(), 'EUR', 'PLN', 'TRANSACTION')"));
      assertTrue(
          singleBoolean(
              statement,
              "SELECT to_regprocedure('investory.resolve_fx_rate_legacy(date,character varying,character varying)') IS NULL"));
      assertTrue(
          singleBoolean(
              statement,
              "SELECT to_regprocedure('investory.resolve_fx_rate_compat_oid(date,character varying,character varying)') IS NULL"));
    }
  }

  @DisplayName("raw Ledger Deletion Is Restricted But Derived Rows Can Be Removed")
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

  @DisplayName("estimated Cash Fx Is Included And Keeps Aggregate Complete")
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
              + "(rate_date, base, to_currency, rate, source, method, source_rate_date) VALUES "
              + "(DATE '2025-01-15', 'EUR', 'USD', 3, 'TEST', 'INTERPOLATED', DATE '2025-01-15') "
              + "ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO UPDATE SET "
              + "rate = EXCLUDED.rate, source = EXCLUDED.source, method = EXCLUDED.method, "
              + "source_rate_date = EXCLUDED.source_rate_date");
      statement.execute(
          "INSERT INTO investory.cash_operations "
              + "(id, account_id, operation, amount, currency, date) "
              + "VALUES (-800002, -800002, 'DEPOSIT', 10, 'EUR', TIMESTAMP WITH TIME ZONE '2025-01-15 00:00:00+00')");

      statement.execute("SELECT investory.refresh_app_views()");
      assertEquals(
          "30.00000000|ESTIMATED",
          singleString(
              statement,
              "SELECT round(amount_in_portfolio_base_currency, 8)::text || '|' || portfolio_conversion_status "
                  + "FROM investory.app_v_normalized_cash_operations WHERE operation_id = -800002"));

      statement.execute("SELECT investory.refresh_app_views()");
      assertEquals(
          "30.00000000|0|true",
          singleString(
              statement,
              "SELECT round(converted_cash_subtotal, 8)::text || '|' || missing_fx_count || '|' || is_complete "
                  + "FROM investory.app_v_account_statistics WHERE account_id = -800002"));
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
        DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
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
}
