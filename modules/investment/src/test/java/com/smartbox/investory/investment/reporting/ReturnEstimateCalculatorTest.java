package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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
  void partialCurrentYearReturnFallsBackToConfiguredBenchmark() {
    var result = calculate("0.173", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 15));

    assertThat(result.historical().status()).isEqualTo(ReturnMetric.Status.INSUFFICIENT_DATA);
    assertThat(result.expected()).isEqualByComparingTo(BENCHMARK);
    assertThat(result.portfolioWeight()).isZero();
  }

  @Test
  void partialHistoryUsesDefaultSevenPercentBenchmarkWhenNoOverrideIsConfigured() {
    var result =
        ReturnEstimateCalculator.calculate(
            ReturnMetric.available(new BigDecimal("0.173")),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 15),
            null);

    assertThat(result.expected()).isEqualByComparingTo("0.07");
    assertThat(result.historical().status()).isEqualTo(ReturnMetric.Status.INSUFFICIENT_DATA);
  }

  @Test
  void anniversaryMakesHistoryEligibleEvenWhenDecimalYearIsJustBelowOne() {
    var result = calculate("0.10", LocalDate.of(2024, 3, 1), LocalDate.of(2025, 3, 1));

    assertThat(result.historical().status()).isEqualTo(ReturnMetric.Status.AVAILABLE);
    assertThat(result.portfolioWeight()).isEqualByComparingTo("0.2");
  }

  @Test
  void configuredTargetHistoryLengthCapsPortfolioWeightAtOneHundredPercent() {
    var fourYears = calculate("0.50", LocalDate.of(2022, 1, 1), LocalDate.of(2026, 1, 1));
    var fiveYears = calculate("1.61051", LocalDate.of(2021, 1, 1), LocalDate.of(2026, 1, 1));

    assertThat(fourYears.portfolioWeight()).isEqualByComparingTo("0.8");
    assertThat(fourYears.benchmarkWeight()).isEqualByComparingTo("0.2");
    assertThat(fiveYears.portfolioWeight()).isEqualByComparingTo("1");
    assertThat(fiveYears.benchmarkWeight()).isZero();
  }

  @Test
  void averagesCurrentYearProjectionWithPriorYearsAndFixedSpyFallbacks() {
    var result =
        ReturnEstimateCalculator.fiveYearAverage(
            2026, new BigDecimal("0.2175"), Map.of(), ReturnEstimateCalculator.SPY_ANNUAL_RETURNS);

    assertThat(result.value()).isEqualByComparingTo("0.1449");
  }

  @Test
  void currentYearAnnualizationUsesOnlyCompletedCalendarMonths() {
    var result =
        ReturnEstimateCalculator.linearAnnualized(
            ReturnMetric.available(new BigDecimal("0.145")),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 15));

    assertThat(result.value()).isEqualByComparingTo("0.2175");
  }

  @Test
  void prefersPortfolioReturnForAnAvailablePriorYear() {
    var result =
        ReturnEstimateCalculator.fiveYearAverage(
            2026,
            new BigDecimal("0.2175"),
            Map.of(2024, new BigDecimal("0.10")),
            ReturnEstimateCalculator.SPY_ANNUAL_RETURNS);

    assertThat(result.value()).isEqualByComparingTo("0.1151");
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
