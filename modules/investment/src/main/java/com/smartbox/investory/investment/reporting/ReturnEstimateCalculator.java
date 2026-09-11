package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/** Centralized historical/expected return policy for forward-looking investment projections. */
public final class ReturnEstimateCalculator {
  public static final BigDecimal DEFAULT_BENCHMARK_EXPECTATION = new BigDecimal("0.07");
  public static final int TARGET_YEARS = 5;
  private static final MathContext CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

  private ReturnEstimateCalculator() {}

  public static Result calculate(
      ReturnMetric cumulativeTwr,
      LocalDate start,
      LocalDate end,
      BigDecimal benchmarkExpectedReturn) {
    BigDecimal years = years(start, end);
    if (years != null
        && end.compareTo(start.plusYears(1)) >= 0
        && years.compareTo(BigDecimal.ONE) < 0) {
      years = BigDecimal.ONE;
    }
    ReturnMetric calculatedHistorical =
        PortfolioReturnCalculator.annualized(cumulativeTwr, start, end);
    boolean usablePortfolioHistory = years != null && years.compareTo(BigDecimal.ONE) >= 0;
    ReturnMetric historical =
        usablePortfolioHistory
            ? calculatedHistorical
            : ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "At least one year of portfolio history is required");
    BigDecimal portfolioWeight =
        usablePortfolioHistory
            ? years
                .min(BigDecimal.valueOf(TARGET_YEARS))
                .divide(BigDecimal.valueOf(TARGET_YEARS), CONTEXT)
            : BigDecimal.ZERO;
    BigDecimal benchmarkWeight = BigDecimal.ONE.subtract(portfolioWeight, CONTEXT);
    BigDecimal benchmark =
        benchmarkExpectedReturn == null ? DEFAULT_BENCHMARK_EXPECTATION : benchmarkExpectedReturn;
    BigDecimal portfolio =
        historical.status() == ReturnMetric.Status.AVAILABLE ? historical.value() : BigDecimal.ZERO;
    BigDecimal expected =
        portfolio
            .multiply(portfolioWeight, CONTEXT)
            .add(benchmark.multiply(benchmarkWeight, CONTEXT));
    return new Result(
        historical,
        years == null ? BigDecimal.ZERO : years,
        expected,
        portfolioWeight,
        benchmarkWeight,
        benchmark);
  }

  private static BigDecimal years(LocalDate start, LocalDate end) {
    if (start == null || end == null || end.isBefore(start)) return null;
    Period period = Period.between(start, end);
    long days = ChronoUnit.DAYS.between(start.plus(period), end);
    if (period.isZero()) return days <= 0 ? null : dayFraction(days);
    return BigDecimal.valueOf(period.getYears())
        .add(BigDecimal.valueOf(period.getMonths()).divide(BigDecimal.valueOf(12), CONTEXT))
        .add(dayFraction(days));
  }

  private static BigDecimal dayFraction(long days) {
    return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365.2425), CONTEXT);
  }

  public record Result(
      ReturnMetric historical,
      BigDecimal historyYears,
      BigDecimal expected,
      BigDecimal portfolioWeight,
      BigDecimal benchmarkWeight,
      BigDecimal benchmarkExpectedReturn) {}
}
