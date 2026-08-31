package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Return Calculator Twr")
class PortfolioReturnCalculatorTwrTest {
  private static final LocalDate DAY = LocalDate.of(2024, 1, 2);

  @DisplayName("calculates Simple Price Increase")
  @Test
  void calculatesSimplePriceIncrease() {
    assertEquals(
        0.10,
        PortfolioReturnCalculator.twr(
                bd("100"), List.of(new DailyPortfolioValue(DAY, bd("110"), bd("0"), bd("0"))))
            .value()
            .doubleValue(),
        0.000001);
  }

  @DisplayName("removes External Contribution From Return")
  @Test
  void removesExternalContributionFromReturn() {
    assertEquals(
        0.10,
        PortfolioReturnCalculator.twr(
                bd("100"), List.of(new DailyPortfolioValue(DAY, bd("165"), bd("50"), bd("0"))))
            .value()
            .doubleValue(),
        0.000001);
  }

  @DisplayName("removes Withdrawal From Return")
  @Test
  void removesWithdrawalFromReturn() {
    assertEquals(
        0.10,
        PortfolioReturnCalculator.twr(
                bd("100"), List.of(new DailyPortfolioValue(DAY, bd("55"), bd("0"), bd("50"))))
            .value()
            .doubleValue(),
        0.000001);
  }

  @DisplayName("internal Transfer Cancels At Portfolio Scope")
  @Test
  void internalTransferCancelsAtPortfolioScope() {
    assertEquals(
        0.10,
        PortfolioReturnCalculator.twr(
                bd("100"), List.of(new DailyPortfolioValue(DAY, bd("110"), bd("50"), bd("50"))))
            .value()
            .doubleValue(),
        0.000001);
  }

  @DisplayName("missing Valuation Is Unavailable")
  @Test
  void missingValuationIsUnavailable() {
    var result =
        PortfolioReturnCalculator.twr(
            bd("100"), List.of(new DailyPortfolioValue(DAY, null, bd("0"), bd("0"))));
    assertNotEquals(ReturnMetric.Status.AVAILABLE, result.status());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
