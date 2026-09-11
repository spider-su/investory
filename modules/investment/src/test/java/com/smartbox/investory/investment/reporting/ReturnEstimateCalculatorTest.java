package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReturnEstimateCalculatorTest {
  private static final BigDecimal BENCHMARK = new BigDecimal("0.08");

  @Test
  void fiveYearsUsePortfolioOnly() {
    var result = calculate("1.61051", LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1));

    assertThat(result.portfolioWeight()).isEqualByComparingTo("1");
    assertThat(result.benchmarkWeight()).isZero();
    assertThat(result.expected()).isEqualByComparingTo(result.historical().value());
  }

  @Test
  void threeYearsBlendPortfolioAndBenchmark() {
    var result = calculate("0.331", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1));

    assertThat(result.portfolioWeight()).isEqualByComparingTo("0.6");
    assertThat(result.benchmarkWeight()).isEqualByComparingTo("0.4");
    assertThat(result.expected())
        .isEqualByComparingTo(
            result
                .historical()
                .value()
                .multiply(new BigDecimal("0.6"))
                .add(BENCHMARK.multiply(new BigDecimal("0.4"))));
  }

  @Test
  void oneYearUsesOneFifthPortfolioAndFourFifthsBenchmark() {
    var result = calculate("0.10", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

    assertThat(result.portfolioWeight()).isEqualByComparingTo("0.2");
    assertThat(result.benchmarkWeight()).isEqualByComparingTo("0.8");
  }

  @Test
  void shortHistoryDoesNotExposeExtrapolatedHistoricalReturn() {
    var result = calculate("0.50", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 1, 1));

    assertThat(result.historical().status()).isEqualTo(ReturnMetric.Status.INSUFFICIENT_DATA);
    assertThat(result.portfolioWeight()).isZero();
    assertThat(result.expected()).isEqualByComparingTo(BENCHMARK);
  }

  @Test
  void negativePortfolioReturnRemainsNegativeInTheBlend() {
    var result = calculate("-0.19", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1));

    assertThat(result.historical().value()).isNegative();
    assertThat(result.expected()).isLessThan(BENCHMARK);
  }

  private static ReturnEstimateCalculator.Result calculate(
      String cumulative, LocalDate start, LocalDate end) {
    return ReturnEstimateCalculator.calculate(
        ReturnMetric.available(new BigDecimal(cumulative)), start, end, BENCHMARK);
  }
}
