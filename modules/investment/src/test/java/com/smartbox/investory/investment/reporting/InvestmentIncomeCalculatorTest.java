package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentIncomeCalculatorTest {
  @Test
  void weightsMixedTransfersByRemainingMonths() {
    var result =
        InvestmentIncomeCalculator.weightedIncomeBase(
            new BigDecimal("100000"),
            List.of(
                new InvestmentIncomeCalculator.MonthlyExternalFlow(
                    YearMonth.of(2026, 3), new BigDecimal("12000")),
                new InvestmentIncomeCalculator.MonthlyExternalFlow(
                    YearMonth.of(2026, 7), new BigDecimal("-6000"))));
    assertThat(result).isEqualByComparingTo("107000.00000000");
  }

  @Test
  void weightsJanuaryAndDecemberAndIgnoresZero() {
    assertThat(
            InvestmentIncomeCalculator.weightedIncomeBase(
                BigDecimal.ZERO,
                List.of(
                    new InvestmentIncomeCalculator.MonthlyExternalFlow(
                        YearMonth.of(2026, 1), new BigDecimal("120")),
                    new InvestmentIncomeCalculator.MonthlyExternalFlow(
                        YearMonth.of(2026, 12), new BigDecimal("120")),
                    new InvestmentIncomeCalculator.MonthlyExternalFlow(
                        YearMonth.of(2026, 6), BigDecimal.ZERO))))
        .isEqualByComparingTo("130.00000000");
  }

  @Test
  void projectsAndReportsFiniteProgressForZeroExpectation() {
    assertThat(
            InvestmentIncomeCalculator.projectedAnnualInvestmentResult(
                new BigDecimal("107000"), new BigDecimal("0.169")))
        .isEqualByComparingTo("18083.00000000");
    assertThat(InvestmentIncomeCalculator.expectedInvestmentResultYtd(new BigDecimal("18083"), 9))
        .isEqualByComparingTo("13562.25000000");
    assertThat(
            InvestmentIncomeCalculator.expectationProgress(
                new BigDecimal("9173"), new BigDecimal("13562.25")))
        .isEqualByComparingTo("0.67636270");
    assertThat(InvestmentIncomeCalculator.expectationProgress(new BigDecimal("1"), BigDecimal.ZERO))
        .isZero();
  }

  @Test
  void calculatesExpectedInvestmentResultFromTotalReturnAssumption() {
    assertThat(
            InvestmentIncomeCalculator.projectedAnnualInvestmentResult(
                new BigDecimal("143900"), new BigDecimal("0.07")))
        .isEqualByComparingTo("10073.00000000");
  }
}
