package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.reporting.PerformancePeriod;
import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Investment Dashboard Application Service")
class InvestmentDashboardApplicationServiceTest {

  private static PerformanceResult result(
      CurrencyType currency, String dividends, String interest, String taxes) {
    return new PerformanceResult(
        new PerformancePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 26)),
        currency,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(dividends),
        new BigDecimal(interest),
        BigDecimal.ZERO,
        new BigDecimal(taxes),
        BigDecimal.ZERO,
        null,
        null,
        null);
  }
}
