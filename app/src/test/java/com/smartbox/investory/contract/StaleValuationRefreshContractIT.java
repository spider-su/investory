package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Stale valuation refresh contract")
class StaleValuationRefreshContractIT {

  private static final long NUCL_ACCOUNT_ID = 990000001L;
  private static final long NUCL_ASSET_ID = 601L;
  private static final long NUCL_POSITION_ID = 990000001L;
  private static final String VALUATION_DATE = "2025-11-03";

  @Test
  @DisplayName("public reconstruction refresh repairs stale NUCL price currency and valuation")
  void publicRefreshRepairsStalePriceCurrencyAndValuation() throws Exception {
    try (WorkerDatabase database = FastDatabase.scopedDatabase("stale_valuation_refresh")) {
      try (Connection connection = database.openConnection();
          Statement statement = connection.createStatement()) {
        seedInconsistentPriceState(statement);

        assertEquals(
            "PLN",
            singleString(
                statement,
                """
            SELECT price_currency
            FROM investory.app_v_normalized_daily_price_mv
            WHERE asset_id = 601 AND valuation_date = DATE '2025-11-03'
            """));

        statement.execute("SELECT investory.refresh_reconstructed_position_daily()");

        assertEquals(
            "USD",
            singleString(
                statement,
                """
            SELECT price_currency
            FROM investory.app_v_canonical_asset_daily_price_mv
            WHERE asset_id = 601 AND price_date = DATE '2025-11-01'
            """));
        assertEquals(
            "USD",
            singleString(
                statement,
                """
            SELECT price_currency
            FROM investory.app_v_normalized_daily_price_mv
            WHERE asset_id = 601 AND valuation_date = DATE '2025-11-03'
            """));
        assertEquals(
            "USD",
            singleString(
                statement,
                """
            SELECT price_currency
            FROM investory.recon_v_reconstructed_position_daily_mv
            WHERE account_id = 990000001 AND asset_id = 601
              AND valuation_date = DATE '2025-11-03'
            """));

        BigDecimal actual =
            singleDecimal(
                statement,
                """
            SELECT reconstructed_market_value_base
            FROM investory.recon_v_reconstructed_position_daily_mv
            WHERE account_id = 990000001 AND asset_id = 601
              AND valuation_date = DATE '2025-11-03'
            """);
        BigDecimal expected =
            new BigDecimal("2").multiply(new BigDecimal("64.16")).multiply(new BigDecimal("4"));
        BigDecimal wrongPlnInterpretation = new BigDecimal("2").multiply(new BigDecimal("64.16"));
        assertEquals(0, expected.compareTo(actual), "USD price must use USD -> PLN FX");
        assertNotEquals(
            0,
            wrongPlnInterpretation.compareTo(actual),
            "reconstruction must not interpret USD price as PLN");

        statement.execute("SELECT investory.refresh_reconstructed_account_market_daily()");
        assertEquals(
            0,
            expected.compareTo(
                singleDecimal(
                    statement,
                    """
            SELECT reconstructed_market_value
            FROM investory.recon_v_reconstructed_account_market_daily_mv
            WHERE account_id = 990000001 AND valuation_date = DATE '2025-11-03'
            """)));
      }
    }
  }

  private static void seedInconsistentPriceState(Statement statement) throws SQLException {
    statement.execute(
        """
        INSERT INTO investory.accounts(
            id, external_account_id, currency, provider, name, owner, portfolio_id)
        VALUES (990000001, 'nucl-p0', 'PLN', 'XTB', 'NUCL P0 account', 'P0 test', 2)
        """);
    statement.execute(
        """
        INSERT INTO investory.account_daily(account_id, snapshot_date, valuation_currency)
        VALUES (990000001, DATE '2025-11-03', 'PLN')
        """);
    statement.execute(
        """
        INSERT INTO investory.positions(
            id, account_id, asset_id, source_asset_symbol, broker_symbol, operation,
            settlement_model, volume, price_currency, cost_currency, profit_currency,
            commission_currency, open_time, open_price, purchase_value)
        VALUES (
            990000001, 990000001, 601, 'NUCL.UK', 'NUCL.UK', 'BUY',
            'CASH_SETTLED', 2, 'USD', 'USD', 'USD', 'USD',
            TIMESTAMPTZ '2025-11-01 10:00:00+00', 64.16, 128.32)
        """);
    statement.execute(
        """
        INSERT INTO investory.exchange_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date)
        VALUES (DATE '2025-11-03', 'USD', 'PLN', 4, 'TEST', 'OBSERVED', DATE '2025-11-03')
        ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO UPDATE
        SET rate = EXCLUDED.rate, source = EXCLUDED.source, method = EXCLUDED.method,
            source_rate_date = EXCLUDED.source_rate_date
        """);
    statement.execute(
        """
        INSERT INTO investory.asset_price_history(
            asset_id, price_date, source, source_symbol, source_mapping_id, price_origin,
            price_currency, close_price, quality_score, quality_class, is_observed,
            price_scale_factor)
        VALUES (601, DATE '2025-11-01', 'TEST', 'nucl-p0', NULL, 'TEST',
                'PLN', 64.16, 95, 'EXACT_LISTING_MARKET_CLOSE', true, 1)
        """);
    refresh(statement, "app_v_portfolio_daily_fx_rate_mv");
    refresh(statement, "app_v_canonical_asset_daily_price_mv");
    refresh(statement, "app_v_canonical_asset_daily_price_ranked_mv");
    refresh(statement, "app_v_normalized_daily_price_mv");
    refresh(statement, "recon_v_reconstructed_position_daily_mv");
    statement.execute(
        """
        UPDATE investory.asset_price_history
        SET price_currency = 'USD'
        WHERE asset_id = 601 AND price_date = DATE '2025-11-01' AND source = 'TEST'
        """);
  }

  private static void refresh(Statement statement, String view) throws SQLException {
    statement.execute("REFRESH MATERIALIZED VIEW investory." + view);
  }

  private static String singleString(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next(), "Expected one row for query: " + sql);
      return result.getString(1);
    }
  }

  private static BigDecimal singleDecimal(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      assertTrue(result.next(), "Expected one row for query: " + sql);
      return result.getBigDecimal(1);
    }
  }
}
