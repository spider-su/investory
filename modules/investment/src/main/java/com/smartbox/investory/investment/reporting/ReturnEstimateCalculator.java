package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Centralized historical/expected return policy for forward-looking investment projections. */
public final class ReturnEstimateCalculator {
  public static final BigDecimal DEFAULT_BENCHMARK_EXPECTATION = new BigDecimal("0.07");
  public static final int TARGET_YEARS = 5;
  private static final MathContext CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

  private ReturnEstimateCalculator() {}

  /** Linear year-end extrapolation for a current partial-year return. */
  public static ReturnMetric linearAnnualized(
      ReturnMetric currentPeriodReturn, LocalDate start, LocalDate end) {
    if (currentPeriodReturn == null
        || currentPeriodReturn.status() != ReturnMetric.Status.AVAILABLE
        || currentPeriodReturn.value() == null
        || start == null
        || end == null
        || end.isBefore(start)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "A valid current-period return and date range are required");
    }
    long months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1;
    if (months < 1 || months > 12) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "Linear annualization requires a partial calendar year");
    }
    return ReturnMetric.available(
        currentPeriodReturn
            .value()
            .multiply(BigDecimal.valueOf(12))
            .divide(BigDecimal.valueOf(months), CONTEXT));
  }

  /**
   * Arithmetic average of five calendar-year returns, using SPY when prior portfolio data is
   * missing.
   */
  public static ReturnMetric fiveYearAverage(
      int currentYear,
      BigDecimal currentYearAnnualizedReturn,
      Map<Integer, BigDecimal> portfolioReturns,
      Map<Integer, BigDecimal> spyReturns) {
    List<BigDecimal> returns =
        java.util.stream.IntStream.rangeClosed(currentYear - 4, currentYear)
            .mapToObj(
                year ->
                    year == currentYear
                        ? currentYearAnnualizedReturn
                        : portfolioReturns.getOrDefault(year, spyReturns.get(year)))
            .filter(value -> value != null)
            .toList();
    if (returns.isEmpty()) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "No annual portfolio or SPY returns are available");
    }
    return ReturnMetric.available(
        returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), CONTEXT));
  }

  public static Result calculate(
      ReturnMetric cumulativeTwr,
      LocalDate start,
      LocalDate end,
      BigDecimal benchmarkExpectedReturn) {
    BigDecimal years = years(start, end);
    // Eligibility uses the actual anniversary boundary. Decimal years are used only for blending;
    // a partial year must never become eligible through rounding or extrapolation.
    boolean usablePortfolioHistory = years != null && end.compareTo(start.plusYears(1)) >= 0;
    BigDecimal usableHistoryYears = usablePortfolioHistory ? years.max(BigDecimal.ONE) : years;
    ReturnMetric historical =
        usablePortfolioHistory
            ? PortfolioReturnCalculator.annualized(cumulativeTwr, start, end)
            : ReturnMetric.unavailable(
                ReturnMetric.Status.INSUFFICIENT_DATA,
                "At least one year of portfolio history is required");
    BigDecimal portfolioWeight =
        usablePortfolioHistory
            ? usableHistoryYears
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
        usableHistoryYears == null ? BigDecimal.ZERO : usableHistoryYears,
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
