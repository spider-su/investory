package com.example.demo.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaMigrationCheckpoint2Test {

  private static final String DB_URL =
      System.getProperty("investory.test.db.url", "jdbc:postgresql://localhost:5432/investory");
  private static final String DB_USERNAME =
      System.getProperty("investory.test.db.username", "postgres");
  private static final String DB_PASSWORD =
      System.getProperty("investory.test.db.password", "postgres");

  @BeforeEach
  void recreateDatabaseFromEmptySchema() throws Exception {
    try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS investory CASCADE");
      statement.execute("CREATE SCHEMA investory");
    }

    Flyway flyway =
        Flyway.configure()
            .cleanDisabled(true)
            .dataSource(DB_URL, DB_USERNAME, DB_PASSWORD)
            .schemas("investory")
            .defaultSchema("investory")
            .locations("classpath:sql/migration")
            .load();
    flyway.migrate();
  }

  @Test
  void appliesAllMigrationsAndCreatesCheckpoint2Invariants() throws Exception {
    try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      assertEquals(4, singleInt(statement, "SELECT count(*) FROM investory.flyway_schema_history"));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'investory'
                AND table_name = 'asset_types'
              """));
      assertEquals(
          11,
          singleInt(
              statement,
              """
              SELECT count(*)
              FROM investory.asset_types
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'accounts'
                AND column_name = 'portfolio_id'
                AND is_nullable = 'NO'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
              JOIN pg_namespace n ON n.oid = t.relnamespace
              WHERE n.nspname = 'investory'
                AND t.relname = 'currencies'
                AND c.conname = 'chk_currencies_id_uppercase_iso'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
              JOIN pg_namespace n ON n.oid = t.relnamespace
              WHERE n.nspname = 'investory'
                AND t.relname = 'asset_source_symbols'
                AND c.conname = 'chk_asset_source_symbols_price_scale_factor_positive'
              """));
      assertTrue(viewExists(statement, "v_normalized_daily_price"));
      assertTrue(viewExists(statement, "v_reconstructed_position_daily"));
      assertTrue(viewExists(statement, "v_position_valuation_validation"));
      assertTrue(viewExists(statement, "v_account_daily_reconciliation"));
      assertTrue(viewExists(statement, "v_non_usd_closed_trade_reconciliation"));
      assertTrue(viewExists(statement, "v_reporting_validation_summary"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "selection_priority"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "selected_price_date"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "underlying_observation_date"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "price_age_days"));
      assertTrue(columnExists(statement, "v_reconstructed_position_daily", "contract_multiplier"));
      assertTrue(columnExists(statement, "v_reconstructed_position_daily", "selected_price_date"));
      assertTrue(columnExists(statement, "v_account_daily_reconciliation", "market_value_difference"));
      assertTrue(columnExists(statement, "v_non_usd_closed_trade_reconciliation", "anomaly_code"));
      assertTrue(columnExists(statement, "v_reporting_validation_summary", "status"));
    }
  }

  @Test
  void classifiesIbkrCashTransferWithEnrichedCommentAsExternalDeposit() throws Exception {
    try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.cash_operations (
              id, account_id, operation, asset_id, amount, currency, comment, date
          ) values (
              -900001, 17959259, 'TRANSFER', null, 7838.285, 'USD',
              'Cash Transfer | ibkrRawType=Deposit | ibkrRawSymbol=- | ibkrGrossAmount=7838.285',
              timestamptz '2025-02-12 00:00:00+01:00'
          )
          """);
      assertEquals(
          "EXTERNAL_DEPOSIT",
          singleString(
              statement,
              """
              select normalized_category
              from investory.normalized_cash_operations
              where operation_id = -900001
              """));
    }
  }

  @Test
  void storesCspxAsUsdAcrossAssetMappingAndHistory() throws Exception {
    try (Connection connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select currency
              from investory.assets
              where symbol = 'CSPX.UK'
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select price_currency
              from investory.asset_source_symbols
              where asset_id = (select id from investory.assets where symbol = 'CSPX.UK')
                and source = 'STOOQ'
                and source_symbol = 'cspx.uk'
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select price_currency
              from investory.asset_price_history
              where asset_id = (select id from investory.assets where symbol = 'CSPX.UK')
                and source = 'STOOQ'
                and source_symbol = 'cspx.uk'
                and price_date = date '2025-11-26'
              """));
    }
  }

  private static boolean viewExists(Statement statement, String viewName) throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM information_schema.views
        WHERE table_schema = 'investory'
          AND table_name = '%s'
        """
            .formatted(viewName));
  }

  private static boolean columnExists(Statement statement, String relationName, String columnName)
      throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'investory'
          AND table_name = '%s'
          AND column_name = '%s'
        """
            .formatted(relationName, columnName));
  }

  private static boolean exists(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    }
  }

  private static int singleInt(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private static String singleString(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }
}
