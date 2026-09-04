package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.fx.FxRateUnavailableException;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDailyFacts;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves that canonical accounting inputs are projected into account_daily. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Synthetic Account Daily Projection Boundary")
class AccountDailyProjectionBoundaryIT extends FastDatabaseTest {

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
    jdbc.update(
        "DELETE FROM investory.exchange_rates WHERE ((base = 'USD' AND to_currency = 'EUR') OR (base = 'EUR' AND to_currency = 'USD'))");
    refresh.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.BROKER_IMPORT);
    currencyRateService.clearValuationResolutionCache();
    assertThrows(
        FxRateUnavailableException.class,
        () -> projections.recalculateAccounts(Set.of(ACCOUNT_ID)));
  }

  private void seedFixture() {
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
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method, source_reference) SELECT day::date, 'USD', 'EUR', .9, 'TEST', 'MARKET_DAILY', 'projection-boundary' FROM generate_series(DATE '2026-08-01', DATE '2026-09-10', interval '1 day') day");
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method, source_reference) SELECT day::date, 'EUR', 'USD', 1.11111111, 'TEST', 'MARKET_DAILY', 'projection-boundary' FROM generate_series(DATE '2026-08-01', DATE '2026-09-10', interval '1 day') day");
    currencyRateService.clearValuationResolutionCache();
  }

  private Map<String, Object> daily(String date) {
    return jdbc.queryForMap(
        "SELECT valuation_currency, cash_balance, market_value, equity, realized_profit, dividends, deposits FROM investory.account_daily WHERE account_id = ? AND snapshot_date = ?",
        ACCOUNT_ID,
        java.sql.Date.valueOf(date));
  }
}
