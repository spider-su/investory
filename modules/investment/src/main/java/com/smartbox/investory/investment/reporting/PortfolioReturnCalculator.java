package com.smartbox.investory.investment.reporting;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure return math over already-normalized portfolio valuation boundaries. */
public final class PortfolioReturnCalculator {
  private static final MathContext MONEY_CONTEXT = new MathContext(28, RoundingMode.HALF_UP);
  private static final double ROOT_TOLERANCE = 1e-10;
  private static final int MAX_ITERATIONS = 100;

  private PortfolioReturnCalculator() {}

  public static ReturnMetric twr(BigDecimal openingValue, List<DailyPortfolioValue> dailyValues) {
    if (openingValue == null || dailyValues == null || dailyValues.isEmpty()) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA, "Opening value and daily valuations are required");
    }
    if (dailyValues.stream()
        .anyMatch(
            row ->
                row == null
                    || row.date() == null
                    || row.endValue() == null
                    || row.contributions() == null
                    || row.withdrawals() == null)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "A daily valuation or normalized flow is missing");
    }
    List<DailyPortfolioValue> rows =
        dailyValues.stream().sorted(Comparator.comparing(DailyPortfolioValue::date)).toList();
    BigDecimal previous = openingValue;
    BigDecimal factor = BigDecimal.ONE;
    for (DailyPortfolioValue row : rows) {
      BigDecimal denominator =
          previous.add(nz(row.contributions())).subtract(nz(row.withdrawals()));
      if (denominator.signum() <= 0) {
        if (row.endValue().signum() == 0 && denominator.signum() == 0) {
          previous = row.endValue();
          continue;
        }
        return ReturnMetric.unavailable(
            ReturnMetric.Status.INSUFFICIENT_DATA,
            "A non-positive valuation boundary prevents TWR calculation");
      }
      factor = factor.multiply(row.endValue().divide(denominator, MONEY_CONTEXT), MONEY_CONTEXT);
      previous = row.endValue();
    }
    return ReturnMetric.available(factor.subtract(BigDecimal.ONE, MONEY_CONTEXT));
  }

  /** Annualizes an already calculated cash-flow-neutral return over its actual date range. */
  public static ReturnMetric annualized(
      ReturnMetric cumulativeReturn, LocalDate startDate, LocalDate endDate) {
    if (cumulativeReturn == null
        || cumulativeReturn.status() != ReturnMetric.Status.AVAILABLE
        || cumulativeReturn.value() == null
        || startDate == null
        || endDate == null
        || endDate.isBefore(startDate)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "A valid cumulative return and date range are required");
    }
    long days = ChronoUnit.DAYS.between(startDate, endDate);
    if (days <= 0 || cumulativeReturn.value().compareTo(BigDecimal.ONE.negate()) <= 0) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA,
          "Annualized return needs a positive elapsed period");
    }
    double years = days / 365.2425d;
    double factor = 1.0 + cumulativeReturn.value().doubleValue();
    double annualized = Math.pow(factor, 1.0 / years) - 1.0;
    if (!Double.isFinite(annualized)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.CALCULATION_FAILED, "Annualized return is not finite");
    }
    return ReturnMetric.available(BigDecimal.valueOf(annualized));
  }

  public static ReturnMetric xirr(
      LocalDate openingDate,
      BigDecimal openingValue,
      LocalDate endingDate,
      BigDecimal endingValue,
      List<DailyPortfolioValue> dailyValues) {
    if (openingDate == null
        || endingDate == null
        || openingValue == null
        || endingValue == null
        || endingDate.isBefore(openingDate)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA, "Valid opening and ending valuation are required");
    }
    List<CashFlow> flows = new ArrayList<>();
    flows.add(new CashFlow(openingDate, openingValue.negate()));
    if (dailyValues != null) {
      if (dailyValues.stream()
          .anyMatch(
              row ->
                  row == null
                      || row.date() == null
                      || row.contributions() == null
                      || row.withdrawals() == null)) {
        return ReturnMetric.unavailable(
            ReturnMetric.Status.INSUFFICIENT_DATA,
            "A daily valuation date or normalized flow is missing");
      }
      dailyValues.stream()
          .filter(row -> !row.date().isBefore(openingDate) && !row.date().isAfter(endingDate))
          .sorted(Comparator.comparing(DailyPortfolioValue::date))
          .forEach(
              row -> {
                BigDecimal contribution = nz(row.contributions());
                BigDecimal withdrawal = nz(row.withdrawals());
                if (contribution.signum() != 0 || withdrawal.signum() != 0) {
                  // Investor cash-flow signs: money entering the portfolio is negative;
                  // money leaving the portfolio is positive.
                  flows.add(new CashFlow(row.date(), withdrawal.subtract(contribution)));
                }
              });
    }
    flows.add(new CashFlow(endingDate, endingValue));
    if (!hasBothSigns(flows)) {
      return ReturnMetric.unavailable(
          ReturnMetric.Status.INSUFFICIENT_DATA, "XIRR needs both investor cash-flow signs");
    }

    Double root = solve(flows, openingDate);
    return root == null
        ? ReturnMetric.unavailable(
            ReturnMetric.Status.CALCULATION_FAILED, "No convergent XIRR root was found")
        : ReturnMetric.available(BigDecimal.valueOf(root).setScale(12, RoundingMode.HALF_UP));
  }

  private static Double solve(List<CashFlow> flows, LocalDate origin) {
    double rate = 0.1;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double value = npv(flows, origin, rate);
      double derivative = derivative(flows, origin, rate);
      if (!Double.isFinite(value) || !Double.isFinite(derivative) || Math.abs(derivative) < 1e-14) {
        break;
      }
      double next = rate - value / derivative;
      if (next <= -0.999999999 || !Double.isFinite(next) || next > 1e6) {
        break;
      }
      if (Math.abs(next - rate) <= ROOT_TOLERANCE
          && Math.abs(npv(flows, origin, next)) <= ROOT_TOLERANCE) {
        return next;
      }
      rate = next;
    }

    double previousRate = -0.9999;
    double previousValue = npv(flows, origin, previousRate);
    for (int i = 1; i <= 2000; i++) {
      double currentRate = -0.9999 + i * 10_000.0 / 2000.0;
      double currentValue = npv(flows, origin, currentRate);
      if (Double.isFinite(previousValue)
          && Double.isFinite(currentValue)
          && previousValue * currentValue <= 0) {
        double low = previousRate;
        double high = currentRate;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
          double middle = (low + high) / 2.0;
          double middleValue = npv(flows, origin, middle);
          if (Math.abs(middleValue) <= ROOT_TOLERANCE) return middle;
          if (previousValue * middleValue <= 0) high = middle;
          else {
            low = middle;
            previousValue = middleValue;
          }
        }
        return (low + high) / 2.0;
      }
      previousRate = currentRate;
      previousValue = currentValue;
    }
    return null;
  }

  private static double npv(List<CashFlow> flows, LocalDate origin, double rate) {
    double base = 1.0 + rate;
    double result = 0.0;
    for (CashFlow flow : flows) {
      double years = ChronoUnit.DAYS.between(origin, flow.date()) / 365.0;
      result += flow.amount().doubleValue() / Math.pow(base, years);
    }
    return result;
  }

  private static double derivative(List<CashFlow> flows, LocalDate origin, double rate) {
    double base = 1.0 + rate;
    double result = 0.0;
    for (CashFlow flow : flows) {
      double years = ChronoUnit.DAYS.between(origin, flow.date()) / 365.0;
      result += -years * flow.amount().doubleValue() / Math.pow(base, years + 1.0);
    }
    return result;
  }

  private static boolean hasBothSigns(List<CashFlow> flows) {
    return flows.stream().anyMatch(flow -> flow.amount().signum() < 0)
        && flows.stream().anyMatch(flow -> flow.amount().signum() > 0);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private record CashFlow(LocalDate date, BigDecimal amount) {}
}
