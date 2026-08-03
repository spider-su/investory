package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

class ValuationInputContractTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_valuation_test")
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
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @Test
  void fxResolverUsesLatestAvailableRateOnOrBeforeValuationDate() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.exchange_rates(month, base, to_currency, rate, source) VALUES "
              + "(DATE '2098-01-01', 'EUR', 'USD', 1.10, 'TEST'), "
              + "(DATE '2098-02-01', 'EUR', 'USD', 1.20, 'TEST')");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT fx_rate_to_target, source_rate_date, conversion_status "
                  + "FROM investory.resolve_fx_rate(DATE '2098-01-15', 'EUR', 'USD')")) {
        assertTrue(result.next());
        assertEquals(
            0, result.getBigDecimal("fx_rate_to_target").compareTo(new BigDecimal("1.10000000")));
        assertEquals("2098-01-01", result.getDate("source_rate_date").toString());
        assertEquals("OK", result.getString("conversion_status"));
      }
    }
  }

  @Test
  void priceLookupUsesLatestAvailableObservationOnOrBeforeValuationDate() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long assetId;
      try (ResultSet result =
          statement.executeQuery("SELECT id FROM investory.assets ORDER BY id LIMIT 1")) {
        assertTrue(result.next());
        assetId = result.getLong(1);
      }

      statement.execute(
          "INSERT INTO investory.asset_price_history ("
              + "asset_id, price_date, source, source_symbol, price_origin, price_currency, "
              + "close_price, estimated, quality_score, quality_class, is_observed, is_proxy) VALUES "
              + "("
              + assetId
              + ", DATE '2030-01-02', 'TEST', 'TEST', 'MARKET_CLOSE', 'USD', "
              + "100, false, 100, 'EXACT_LISTING_MARKET_CLOSE', true, false), "
              + "("
              + assetId
              + ", DATE '2030-01-10', 'TEST2', 'TEST', 'MARKET_CLOSE', 'USD', "
              + "110, false, 100, 'EXACT_LISTING_MARKET_CLOSE', true, false)");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT price_date, close_price "
                  + "FROM investory.v_canonical_asset_daily_price "
                  + "WHERE asset_id = "
                  + assetId
                  + " AND price_date <= DATE '2030-01-05' "
                  + "ORDER BY price_date DESC LIMIT 1")) {
        assertTrue(result.next());
        assertEquals("2030-01-02", result.getDate("price_date").toString());
        assertEquals(0, result.getBigDecimal("close_price").compareTo(new BigDecimal("100")));
      }
    }
  }

  @Test
  void missingFxMakesPortfolioDailyTotalsIncompleteRatherThanPartial() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO investory.currencies(id) VALUES ('GBP')");
      statement.execute(
          "INSERT INTO investory.accounts(id, currency, provider, name, owner, portfolio_id) "
              + "SELECT 999999, 'GBP', 'XTB', 'Missing FX test', 'Alex', id "
              + "FROM investory.portfolios ORDER BY id LIMIT 1");
      statement.execute(
          "INSERT INTO investory.account_daily("
              + "account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity) "
              + "VALUES (999999, DATE '2030-01-31', 'GBP', 10, 20, 30)");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT equity, converted_equity_subtotal, missing_fx_count, is_complete "
                  + "FROM investory.v_portfolio_daily "
                  + "WHERE snapshot_date = DATE '2030-01-31'")) {
        assertTrue(result.next());
        assertEquals(null, result.getBigDecimal("equity"));
        assertEquals(null, result.getBigDecimal("converted_equity_subtotal"));
        assertEquals(1, result.getLong("missing_fx_count"));
        assertEquals(false, result.getBoolean("is_complete"));
      }
    }
  }

  @Test
  void diagnosticsAndMonthlyReviewExposeMissingInputs() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.currencies(id) VALUES ('GBP') ON CONFLICT DO NOTHING");
      statement.execute(
          "INSERT INTO investory.cash_operations(account_id, operation, amount, currency, comment, date) "
              + "SELECT id, 'DEPOSIT', 10, 'GBP', 'missing fx diagnostic', "
              + "TIMESTAMPTZ '2030-01-31 12:00:00+00' "
              + "FROM investory.accounts ORDER BY id LIMIT 1");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM investory.reporting_valuation_input_issues "
                  + "WHERE issue_code = 'MISSING_FX' AND severity = 'ERROR'")) {
        assertTrue(result.next());
        assertTrue(result.getLong(1) >= 1);
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT issue_count FROM investory.reporting_monthly_import_review "
                  + "WHERE check_code = 'VALUATION_INPUT_ERROR'")) {
        assertTrue(result.next());
        assertTrue(result.getLong(1) >= 1);
      }
    }
  }

  @Test
  void strictRefreshCanBlockOnMissingInputsWhileDefaultRefreshRemainsAvailable()
      throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.currencies(id) VALUES ('GBP') ON CONFLICT DO NOTHING");
      statement.execute(
          "INSERT INTO investory.cash_operations(account_id, operation, amount, currency, comment, date) "
              + "SELECT id, 'DEPOSIT', 10, 'GBP', 'strict refresh missing fx', "
              + "TIMESTAMPTZ '2031-01-31 12:00:00+00' "
              + "FROM investory.accounts ORDER BY id LIMIT 1");

      boolean blocked = false;
      try {
        statement.execute("CALL investory.refresh_reporting_materialized_views(true)");
      } catch (SQLException expected) {
        blocked = expected.getMessage().contains("missing required valuation inputs");
      }
      assertTrue(blocked);

      statement.execute("CALL investory.refresh_reporting_materialized_views(false)");
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
