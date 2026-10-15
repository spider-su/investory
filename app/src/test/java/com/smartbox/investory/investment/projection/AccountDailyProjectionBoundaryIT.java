package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.fx.FxRateUnavailableException;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDailyFacts;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Proves that canonical accounting inputs are projected into account_daily. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-fast")
@DisplayName("Synthetic Account Daily Projection Boundary")
class AccountDailyProjectionBoundaryIT {

  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("account_daily_projection_boundary");

  private static final long PORTFOLIO_ID = -920001L;
  private static final long ACCOUNT_ID = -920002L;
  private static final long DEPOSIT_ID = -920010L;
  private static final long DIVIDEND_ID = -920011L;
  private static final long RESULT_ID = -920012L;
  private static final long POSITION_ID = -920020L;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService refresh;
  @Autowired private CurrencyRateService currencyRateService;

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @AfterEach
  void removeProjectionFixture() {
    jdbc.update("DELETE FROM investory.account_daily WHERE account_id = ?", ACCOUNT_ID);
    jdbc.update("DELETE FROM investory.asset_price_history WHERE source = 'BOUNDARY_TEST'");
    jdbc.update(
        "DELETE FROM investory.exchange_rates WHERE source = 'TEST' AND source_reference = 'projection-boundary'");
    jdbc.update("DELETE FROM investory.positions WHERE id = ?", POSITION_ID);
    jdbc.update(
        "DELETE FROM investory.cash_operations WHERE id IN (?, ?, ?)",
        DEPOSIT_ID,
        DIVIDEND_ID,
        RESULT_ID);
    jdbc.update("DELETE FROM investory.accounts WHERE id = ?", ACCOUNT_ID);
    jdbc.update("DELETE FROM investory.portfolios WHERE id = ?", PORTFOLIO_ID);
  }

  @Test
  @DisplayName("rebuilds exact cash value flows and is idempotent for one account")
  void rebuildsExactCashValueFlowsAndIsIdempotentForOneAccount() {
    seedFixture();
    refresh.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.BROKER_IMPORT);

    projections.recalculateAccounts(Set.of(ACCOUNT_ID));
    Map<String, Object> row = daily("2026-08-10");
    assertThat(row.get("valuation_currency")).isEqualTo("EUR");
    assertThat((BigDecimal) row.get("cash_balance"))
        .isEqualByComparingTo(HappyInvestorDailyFacts.CASH_BALANCE);
    assertThat((BigDecimal) row.get("market_value"))
        .isEqualByComparingTo(HappyInvestorDailyFacts.MARKET_VALUE);
    assertThat((BigDecimal) row.get("equity")).isEqualByComparingTo(HappyInvestorDailyFacts.EQUITY);
    assertThat((BigDecimal) row.get("realized_profit"))
        .isEqualByComparingTo(BigDecimal.ZERO.setScale(8));
    assertThat((BigDecimal) daily("2026-08-05").get("dividends"))
        .isEqualByComparingTo(HappyInvestorDailyFacts.DIVIDENDS);
    assertThat((BigDecimal) daily("2026-08-01").get("deposits"))
        .isEqualByComparingTo(HappyInvestorDailyFacts.DEPOSITS);
    assertThat((BigDecimal) daily("2026-08-06").get("realized_profit"))
        .isEqualByComparingTo(HappyInvestorDailyFacts.REALIZED_PROFIT);
    assertThat(dailyProjection("2026-08-10"))
        .isEqualTo(
            new DailyProjection(
                java.time.LocalDate.parse("2026-08-10"),
                "EUR",
                new BigDecimal("927.00000000"),
                new BigDecimal("216.00000000"),
                new BigDecimal("1143.00000000"),
                new BigDecimal("180.00000000"),
                new BigDecimal("36.00000000"),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                new BigDecimal("216.00000000"),
                new BigDecimal("0.23300971"),
                null));
    List<DailyProjection> before = dailyProjectionSnapshot();
    long rows =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.account_daily WHERE account_id = ?",
            Long.class,
            ACCOUNT_ID);
    projections.recalculateAccounts(Set.of(ACCOUNT_ID));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.account_daily WHERE account_id = ?",
                Long.class,
                ACCOUNT_ID))
        .isEqualTo(rows);
    assertThat(daily("2026-08-10")).isEqualTo(row);
    assertThat(dailyProjectionSnapshot()).isEqualTo(before);
    assertReconciliationBridge("2026-08-10");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.account_daily d JOIN investory.accounts a ON a.id = d.account_id WHERE a.portfolio_id = ? AND d.account_id <> ?",
                Long.class,
                PORTFOLIO_ID,
                ACCOUNT_ID))
        .isZero();
  }

  @Test
  @DisplayName("missing FX fails closed instead of valuing USD as EUR")
  void missingFxFailsClosed() {
    seedFixture();
    List<Map<String, Object>> removedRates =
        jdbc.queryForList(
            "SELECT rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference "
                + "FROM investory.exchange_rates "
                + "WHERE ((base = 'USD' AND to_currency = 'EUR') "
                + "OR (base = 'EUR' AND to_currency = 'USD'))");
    try {
      jdbc.update(
          "DELETE FROM investory.exchange_rates WHERE ((base = 'USD' AND to_currency = 'EUR') OR (base = 'EUR' AND to_currency = 'USD'))");
      refresh.refreshApplicationViews(
          PortfolioProjectionRefreshService.ApplicationRefreshScope.BROKER_IMPORT);
      currencyRateService.clearValuationResolutionCache();
      assertThrows(
          FxRateUnavailableException.class,
          () -> projections.recalculateAccounts(Set.of(ACCOUNT_ID)));
    } finally {
      for (Map<String, Object> row : removedRates) {
        jdbc.update(
            "INSERT INTO investory.exchange_rates "
                + "(rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            row.get("rate_date"),
            row.get("base"),
            row.get("to_currency"),
            row.get("rate"),
            row.get("source"),
            row.get("method"),
            row.get("source_rate_date"),
            row.get("source_reference"));
      }
      currencyRateService.clearValuationResolutionCache();
    }
  }

  private void seedFixture() {
    jdbc.update(
        "DELETE FROM investory.exchange_rates WHERE rate_date BETWEEN DATE '2026-08-01' AND DATE '2026-09-10' "
            + "AND ((base = 'USD' AND to_currency = 'EUR') OR (base = 'EUR' AND to_currency = 'USD'))");
    jdbc.update(
        "INSERT INTO investory.portfolios(id, name, base_currency, local_currency, owner, user_id) VALUES (?, 'Projection Boundary', 'EUR', 'PLN', 'test', 1)",
        PORTFOLIO_ID);
    jdbc.update(
        "INSERT INTO investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) VALUES (?, 'boundary-920002', 'USD', 'IBKR', 'Projection Boundary Account', 'test', ?, false)",
        ACCOUNT_ID,
        PORTFOLIO_ID);
    Long assetId =
        jdbc.queryForObject(
            "SELECT id FROM investory.assets WHERE symbol = 'VWRA.UK' LIMIT 1", Long.class);
    jdbc.update(
        "INSERT INTO investory.cash_operations(id, account_id, operation, amount, currency, comment, date) VALUES (?, ?, 'DEPOSIT', 1000, 'USD', 'external deposit', TIMESTAMPTZ '2026-08-01 12:00:00Z'), (?, ?, 'DIVIDEND', 10, 'USD', 'income', TIMESTAMPTZ '2026-08-05 12:00:00Z'), (?, ?, 'CLOSE_TRADE', 20, 'USD', 'realized result', TIMESTAMPTZ '2026-08-06 12:00:00Z')",
        DEPOSIT_ID,
        ACCOUNT_ID,
        DIVIDEND_ID,
        ACCOUNT_ID,
        RESULT_ID,
        ACCOUNT_ID);
    jdbc.update(
        "INSERT INTO investory.positions(id, account_id, asset_id, source_asset_symbol, broker_symbol, operation, settlement_model, volume, price_currency, cost_currency, profit_currency, commission_currency, open_time, open_price, purchase_value, profit) VALUES (?, ?, ?, 'VWRA.UK', 'VWRA.UK', 'BUY', 'CASH_SETTLED', 2, 'USD', 'USD', 'USD', 'USD', TIMESTAMPTZ '2026-08-02 12:00:00Z', 100, 200, 0)",
        POSITION_ID,
        ACCOUNT_ID,
        assetId);
    jdbc.update(
        "INSERT INTO investory.asset_price_history(asset_id, price_date, source, source_symbol, price_origin, price_currency, close_price, source_date, quality_score, quality_class) VALUES (?, DATE '2026-08-10', 'BOUNDARY_TEST', 'VWRA.UK', 'MARKET_CLOSE', 'USD', 120, DATE '2026-08-10', 100, 'EXACT_LISTING_MARKET_CLOSE')",
        assetId);
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference) SELECT day::date, 'USD', 'EUR', .9, 'TEST', 'OBSERVED', day::date, 'projection-boundary' FROM generate_series(DATE '2026-08-01', DATE '2026-09-10', interval '1 day') day");
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference) SELECT day::date, 'EUR', 'USD', 1.11111111, 'TEST', 'OBSERVED', day::date, 'projection-boundary' FROM generate_series(DATE '2026-08-01', DATE '2026-09-10', interval '1 day') day");
    currencyRateService.clearValuationResolutionCache();
  }

  private Map<String, Object> daily(String date) {
    return jdbc.queryForMap(
        "SELECT valuation_currency, cash_balance, market_value, equity, realized_profit, dividends, deposits FROM investory.account_daily WHERE account_id = ? AND snapshot_date = ?",
        ACCOUNT_ID,
        java.sql.Date.valueOf(date));
  }

  private DailyProjection dailyProjection(String date) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT snapshot_date, valuation_currency, cash_balance, market_value, equity, cost_base, unrealized_profit, realized_profit, dividends, interest, fees, taxes, deposits, withdrawals, daily_profit_amount, daily_return_pct, portfolio_weight "
                + "FROM investory.account_daily WHERE account_id = ? AND snapshot_date = ?",
            ACCOUNT_ID,
            java.sql.Date.valueOf(date));
    return new DailyProjection(
        ((java.sql.Date) row.get("snapshot_date")).toLocalDate(),
        (String) row.get("valuation_currency"),
        (BigDecimal) row.get("cash_balance"),
        (BigDecimal) row.get("market_value"),
        (BigDecimal) row.get("equity"),
        (BigDecimal) row.get("cost_base"),
        (BigDecimal) row.get("unrealized_profit"),
        (BigDecimal) row.get("realized_profit"),
        (BigDecimal) row.get("dividends"),
        (BigDecimal) row.get("interest"),
        (BigDecimal) row.get("fees"),
        (BigDecimal) row.get("taxes"),
        (BigDecimal) row.get("deposits"),
        (BigDecimal) row.get("withdrawals"),
        (BigDecimal) row.get("daily_profit_amount"),
        (BigDecimal) row.get("daily_return_pct"),
        (BigDecimal) row.get("portfolio_weight"));
  }

  private List<DailyProjection> dailyProjectionSnapshot() {
    return jdbc.query(
        "SELECT snapshot_date, valuation_currency, cash_balance, market_value, equity, cost_base, unrealized_profit, realized_profit, dividends, interest, fees, taxes, deposits, withdrawals, daily_profit_amount, daily_return_pct, portfolio_weight "
            + "FROM investory.account_daily WHERE account_id = ? ORDER BY snapshot_date",
        (resultSet, rowNum) ->
            new DailyProjection(
                resultSet.getDate("snapshot_date").toLocalDate(),
                resultSet.getString("valuation_currency"),
                resultSet.getBigDecimal("cash_balance"),
                resultSet.getBigDecimal("market_value"),
                resultSet.getBigDecimal("equity"),
                resultSet.getBigDecimal("cost_base"),
                resultSet.getBigDecimal("unrealized_profit"),
                resultSet.getBigDecimal("realized_profit"),
                resultSet.getBigDecimal("dividends"),
                resultSet.getBigDecimal("interest"),
                resultSet.getBigDecimal("fees"),
                resultSet.getBigDecimal("taxes"),
                resultSet.getBigDecimal("deposits"),
                resultSet.getBigDecimal("withdrawals"),
                resultSet.getBigDecimal("daily_profit_amount"),
                resultSet.getBigDecimal("daily_return_pct"),
                resultSet.getBigDecimal("portfolio_weight")),
        ACCOUNT_ID);
  }

  private void assertReconciliationBridge(String date) {
    Map<String, Object> reconciliation =
        jdbc.queryForMap(
            "SELECT equity_difference, cash_difference, market_value_difference, unrealized_difference "
                + "FROM investory.recon_v_account_daily WHERE account_id = ? AND valuation_date = ?",
            ACCOUNT_ID,
            java.sql.Date.valueOf(date));
    assertThat((BigDecimal) reconciliation.get("equity_difference"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) reconciliation.get("cash_difference"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) reconciliation.get("market_value_difference"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat((BigDecimal) reconciliation.get("unrealized_difference"))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  private record DailyProjection(
      java.time.LocalDate date,
      String currency,
      BigDecimal cash,
      BigDecimal marketValue,
      BigDecimal equity,
      BigDecimal costBase,
      BigDecimal unrealizedProfit,
      BigDecimal realizedProfit,
      BigDecimal dividends,
      BigDecimal interest,
      BigDecimal fees,
      BigDecimal taxes,
      BigDecimal deposits,
      BigDecimal withdrawals,
      BigDecimal dailyProfit,
      BigDecimal dailyReturn,
      BigDecimal portfolioWeight) {}
}
