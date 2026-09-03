package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Valuation Input Contract")
class ValuationInputContractIT {

  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("valuation_input_contract");

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DisplayName("fx Resolver Uses Latest Available Rate On Or Before Valuation Date")
  @Test
  void fxResolverUsesLatestAvailableRateOnOrBeforeValuationDate() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "DELETE FROM investory.exchange_rates "
              + "WHERE rate_date <= DATE '2098-01-15' "
              + "AND ((base = 'EUR' AND to_currency = 'USD') "
              + "OR (base = 'USD' AND to_currency = 'EUR'))");
      statement.execute(
          "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2098-01-01', 'EUR', 'USD', 1.10, 'TEST', 'MARKET_DAILY'), "
              + "(DATE '2098-02-01', 'EUR', 'USD', 1.20, 'TEST', 'MARKET_DAILY')");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT fx_rate_to_target, source_rate_date, conversion_status "
                  + "FROM investory.resolve_fx_rate(DATE '2098-01-15', 'EUR', 'USD')")) {
        assertTrue(result.next());
        assertEquals(
            0, result.getBigDecimal("fx_rate_to_target").compareTo(new BigDecimal("1.10000000")));
        assertEquals("2098-01-01", result.getDate("source_rate_date").toString());
        assertEquals("STALE", result.getString("conversion_status"));
      }
    }
  }

  @DisplayName("price Lookup Uses Latest Available Observation On Or Before Valuation Date")
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
      statement.execute("SELECT investory.refresh_app_views()");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT price_date, close_price "
                  + "FROM investory.app_v_canonical_asset_daily_price "
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

  @DisplayName("valuation Fx Uses Documented Source And Freshness Precedence")
  @Test
  void valuationFxUsesDocumentedSourceAndFreshnessPrecedence() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2099-01-05', 'EUR', 'USD', 1.10, 'TEST', 'MARKET_DAILY'), "
              + "(DATE '2099-01-10', 'EUR', 'USD', 1.20, 'TEST', 'IBKR_DAILY_REFERENCE'), "
              + "(DATE '2099-01-10', 'EUR', 'USD', 1.30, 'TEST', 'MARKET_DAILY'), "
              + "(DATE '2099-01-10', 'USD', 'PLN', 4.00, 'TEST', 'IBKR_EXECUTION')");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT fx_rate_to_target, rate_method, conversion_status FROM investory.resolve_fx_rate(DATE '2099-01-10', 'EUR', 'USD')")) {
        assertTrue(result.next());
        assertEquals(
            0, result.getBigDecimal("fx_rate_to_target").compareTo(new BigDecimal("1.30")));
        assertEquals("MARKET_DAILY", result.getString("rate_method"));
        assertEquals("OK", result.getString("conversion_status"));
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT fx_rate_to_target, rate_method, conversion_status FROM investory.resolve_fx_rate(DATE '2099-01-10', 'USD', 'PLN')")) {
        assertTrue(result.next());
        assertNotEquals("IBKR_EXECUTION", result.getString("rate_method"));
      }

      statement.execute(
          "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method) VALUES "
              + "(DATE '2099-01-12', 'EUR', 'USD', 1.15, 'TEST', 'MARKET_DAILY'), "
              + "(DATE '2099-01-05', 'EUR', 'PLN', 4.10, 'NBP', 'HISTORICAL_MONTHLY'), "
              + "(DATE '2099-02-01', 'EUR', 'PLN', 4.20, 'NBP', 'HISTORICAL_MONTHLY')");
      try (ResultSet result =
          statement.executeQuery(
              "SELECT rate_method, conversion_status FROM investory.resolve_fx_rate(DATE '2099-01-14', 'EUR', 'USD')")) {
        assertTrue(result.next());
        assertEquals("CARRY_FORWARD", result.getString("rate_method"));
        assertEquals("OK", result.getString("conversion_status"));
      }
      try (ResultSet result =
          statement.executeQuery(
              "SELECT conversion_status FROM investory.resolve_fx_rate(DATE '2099-01-10', 'EUR', 'PLN')")) {
        assertTrue(result.next());
        assertEquals("ESTIMATED", result.getString("conversion_status"));
      }
      try (ResultSet result =
          statement.executeQuery(
              "SELECT rate_method FROM investory.resolve_fx_rate(DATE '2099-01-10', 'USD', 'PLN')")) {
        assertTrue(result.next());
        assertNotEquals("IBKR_EXECUTION", result.getString("rate_method"));
      }
    }
  }

  @DisplayName("current Price Uses Fresh Observed History Then Native Asset Fallback")
  @Test
  void currentPriceUsesFreshObservedHistoryThenNativeAssetFallback() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.assets "
              + "(name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, market_price) VALUES "
              + "('Current price contract', 'CURPRICE.US', 'CURPRICE', 'CURPRICE', 'CURPRICE.US', "
              + "'US', 'USD', 'EQUITY', 60)");
      statement.execute(
          "INSERT INTO investory.asset_source_symbols "
              + "(asset_id, source, source_symbol, price_currency) "
              + "SELECT id, 'TESTMAP', 'curprice.us', 'USD' "
              + "FROM investory.assets WHERE symbol = 'CURPRICE.US'");
      statement.execute(
          "INSERT INTO investory.asset_price_history "
              + "(asset_id, price_date, source, source_symbol, price_origin, price_currency, "
              + "close_price, estimated, quality_score, quality_class, is_observed, is_proxy) "
              + "SELECT id, CURRENT_DATE, 'TESTMAP', 'curprice.us', 'MARKET_CLOSE', 'USD', "
              + "100, false, 100, 'EXACT_LISTING_MARKET_CLOSE', true, false "
              + "FROM investory.assets WHERE symbol = 'CURPRICE.US'");
      statement.execute("SELECT investory.refresh_app_views()");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT selected_price, price_currency, source_mapping_id IS NOT NULL AS mapped "
                  + "FROM investory.app_v_current_asset_price "
                  + "WHERE asset_id = (SELECT id FROM investory.assets WHERE symbol = 'CURPRICE.US')")) {
        assertTrue(result.next());
        assertEquals(0, result.getBigDecimal("selected_price").compareTo(new BigDecimal("100")));
        assertEquals("USD", result.getString("price_currency"));
        assertTrue(result.getBoolean("mapped"));
      }

      statement.execute(
          "UPDATE investory.asset_price_history SET price_date = CURRENT_DATE - 11 "
              + "WHERE source = 'TESTMAP' AND source_symbol = 'curprice.us'");
      statement.execute("SELECT investory.refresh_app_views()");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT selected_price, price_currency, price_selection_source "
                  + "FROM investory.app_v_current_asset_price "
                  + "WHERE asset_id = (SELECT id FROM investory.assets WHERE symbol = 'CURPRICE.US')")) {
        assertTrue(result.next());
        assertEquals(0, result.getBigDecimal("selected_price").compareTo(new BigDecimal("60")));
        assertEquals("USD", result.getString("price_currency"));
        assertEquals("ASSET_CURRENT_FALLBACK", result.getString("price_selection_source"));
      }
    }
  }

  @DisplayName("emim Generator Rows Use Normalized Scale Metadata")
  @Test
  void emimGeneratorRowsUseNormalizedScaleMetadata() throws SQLException {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT ass.price_scale_factor, aph.price_scale_factor, aph.close_price, "
                    + "aph.price_currency "
                    + "FROM investory.asset_source_symbols ass "
                    + "JOIN investory.asset_price_history aph "
                    + "  ON aph.asset_id = ass.asset_id "
                    + " AND aph.source = ass.source "
                    + " AND lower(aph.source_symbol) = lower(ass.source_symbol) "
                    + "WHERE ass.asset_id = (SELECT id FROM investory.assets WHERE symbol = 'EMIM.UK') "
                    + "  AND ass.source = 'STOOQ' "
                    + "  AND lower(ass.source_symbol) = 'emim.uk' "
                    + "ORDER BY aph.price_date DESC LIMIT 1")) {
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertEquals(
            0, result.getBigDecimal("price_scale_factor").compareTo(new BigDecimal("0.01")));
        assertEquals(0, result.getBigDecimal(2).compareTo(new BigDecimal("0.01")));
        assertEquals(0, result.getBigDecimal("close_price").compareTo(new BigDecimal("2724")));
        assertEquals("USD", result.getString("price_currency"));
      }
    }
  }

  @DisplayName("current Price Applies Scale Exactly Once For Scaled And Unscaled Rows")
  @Test
  void currentPriceAppliesScaleExactlyOnceForScaledAndUnscaledRows() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.assets "
              + "(name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, market_price) VALUES "
              + "('Scaled contract', 'SCALECONTRACT.US', 'SCALECONTRACT', 'SCALECONTRACT', 'SCALECONTRACT.US', "
              + "'US', 'USD', 'EQUITY', 1), "
              + "('Unscaled contract', 'UNSCALECONTRACT.US', 'UNSCALECONTRACT', 'UNSCALECONTRACT', 'UNSCALECONTRACT.US', "
              + "'US', 'USD', 'EQUITY', 1)");
      statement.execute(
          "INSERT INTO investory.asset_source_symbols "
              + "(asset_id, source, source_symbol, price_currency, price_scale_factor) "
              + "SELECT id, 'TESTSCALE', lower(symbol), 'USD', "
              + "CASE WHEN symbol = 'SCALECONTRACT.US' THEN 0.01 ELSE 1 END "
              + "FROM investory.assets WHERE symbol IN ('SCALECONTRACT.US', 'UNSCALECONTRACT.US')");
      statement.execute(
          "INSERT INTO investory.asset_price_history "
              + "(asset_id, price_date, source, source_symbol, price_origin, price_currency, close_price, "
              + "estimated, quality_score, quality_class, is_observed, is_proxy, price_scale_factor) "
              + "SELECT id, CURRENT_DATE, 'TESTSCALE', lower(symbol), 'MARKET_CLOSE', 'USD', "
              + "CASE WHEN symbol = 'SCALECONTRACT.US' THEN 2724 ELSE 100 END, "
              + "false, 100, 'EXACT_LISTING_MARKET_CLOSE', true, false, "
              + "CASE WHEN symbol = 'SCALECONTRACT.US' THEN 0.01 ELSE 1 END "
              + "FROM investory.assets WHERE symbol IN ('SCALECONTRACT.US', 'UNSCALECONTRACT.US')");
      statement.execute("SELECT investory.refresh_app_views()");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT a.symbol, p.selected_price "
                  + "FROM investory.app_v_current_asset_price p "
                  + "JOIN investory.assets a ON a.id = p.asset_id "
                  + "WHERE a.symbol IN ('SCALECONTRACT.US', 'UNSCALECONTRACT.US') "
                  + "ORDER BY a.symbol")) {
        assertTrue(result.next());
        assertEquals("SCALECONTRACT.US", result.getString("symbol"));
        assertEquals(0, result.getBigDecimal("selected_price").compareTo(new BigDecimal("27.24")));
        assertTrue(result.next());
        assertEquals("UNSCALECONTRACT.US", result.getString("symbol"));
        assertEquals(0, result.getBigDecimal("selected_price").compareTo(new BigDecimal("100")));
      }
    }
  }

  @DisplayName("no Dot Canonical Symbols Do Not Use Unjustified Security Taxonomy")
  @Test
  void noDotCanonicalSymbolsDoNotUseUnjustifiedSecurityTaxonomy() throws SQLException {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT symbol, asset_type FROM investory.assets "
                    + "WHERE symbol NOT LIKE '%.%' "
                    + "AND asset_type IN ('EQUITY', 'ETF', 'REIT', 'FUND') "
                    + "ORDER BY symbol")) {
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(
            !result.next(),
            "No-dot canonical symbols classified as securities require explicit taxonomy justification");
      }
    }
  }

  @DisplayName("ibkr Identifiers Resolve To One Canonical Asset")
  @Test
  void ibkrIdentifiersResolveToOneCanonicalAsset() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT ibkr, count(*) FROM investory.assets "
                    + "WHERE btrim(ibkr) <> '' GROUP BY ibkr HAVING count(*) > 1")) {
      assertTrue(!result.next(), "An IBKR identifier must not map to multiple assets");
    }
  }

  @DisplayName("seeded Treasury Has One Canonical Bond Identity")
  @Test
  void seededTreasuryHasOneCanonicalBondIdentity() throws SQLException {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT symbol, ticker, ibkr, isin, asset_type "
                    + "FROM investory.assets WHERE ibkr = 'T458022826'")) {
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertEquals("US91282CKB62", result.getString("symbol"));
        assertEquals("US91282CKB62", result.getString("ticker"));
        assertEquals("T458022826", result.getString("ibkr"));
        assertEquals("US91282CKB62", result.getString("isin"));
        assertEquals("BOND", result.getString("asset_type"));
        assertTrue(!result.next(), "Treasury IBKR identity must have one canonical row");
      }
    }
  }

  @DisplayName("missing Fx Makes Portfolio Daily Totals Incomplete Rather Than Partial")
  @Test
  void missingFxMakesPortfolioDailyTotalsIncompleteRatherThanPartial() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO investory.currencies(id) VALUES ('GBP')");
      statement.execute(
          "INSERT INTO investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id) "
              + "SELECT 999999, '999999', 'GBP', 'XTB', 'Missing FX test', 'Sample User', id "
              + "FROM investory.portfolios ORDER BY id LIMIT 1");
      statement.execute(
          "INSERT INTO investory.account_daily("
              + "account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity) "
              + "VALUES (999999, DATE '2030-01-31', 'GBP', 10, 20, 30)");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT equity, converted_equity_subtotal, missing_fx_count, is_complete "
                  + "FROM investory.app_v_portfolio_daily "
                  + "WHERE snapshot_date = DATE '2030-01-31'")) {
        assertTrue(result.next());
        assertEquals(null, result.getBigDecimal("equity"));
        assertEquals(null, result.getBigDecimal("converted_equity_subtotal"));
        assertEquals(1, result.getLong("missing_fx_count"));
        assertEquals(false, result.getBoolean("is_complete"));
      }
    }
  }

  @DisplayName("diagnostics And Monthly Review Expose Missing Inputs")
  @Test
  void diagnosticsAndMonthlyReviewExposeMissingInputs() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.currencies(id) VALUES ('GBP') ON CONFLICT DO NOTHING");
      statement.execute(
          "INSERT INTO investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id) "
              + "SELECT 999998, '999998', 'GBP', 'XTB', 'Missing FX diagnostic', 'Sample User', id "
              + "FROM investory.portfolios ORDER BY id LIMIT 1 "
              + "ON CONFLICT (id) DO NOTHING");
      statement.execute(
          "INSERT INTO investory.cash_operations(id, account_id, operation, amount, currency, comment, date) "
              + "SELECT 990000000000000001, id, 'DEPOSIT', 10, 'GBP', 'missing fx diagnostic', "
              + "TIMESTAMPTZ '2030-01-31 12:00:00+00' "
              + "FROM investory.accounts WHERE id = 999998");
      statement.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT normalized_category, portfolio_conversion_status "
                  + "FROM investory.app_v_normalized_cash_operations "
                  + "WHERE operation_id = 990000000000000001")) {
        assertTrue(result.next());
        assertEquals("EXTERNAL_DEPOSIT", result.getString(1));
        assertEquals("MISSING_RATE", result.getString(2));
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM investory.recon_v_valuation_input_issues "
                  + "WHERE issue_code = 'MISSING_FX' AND severity = 'ERROR'")) {
        assertTrue(result.next());
        assertTrue(result.getLong(1) >= 1);
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT issue_count FROM investory.recon_v_reporting_monthly_import_review "
                  + "WHERE check_code = 'VALUATION_INPUT_ERROR'")) {
        assertTrue(result.next());
        assertTrue(result.getLong(1) >= 1);
      }
    }
  }

  private static Connection connection() throws SQLException {
    Connection connection = DATABASE.openConnection();
    connection.setAutoCommit(false);
    return connection;
  }
}
