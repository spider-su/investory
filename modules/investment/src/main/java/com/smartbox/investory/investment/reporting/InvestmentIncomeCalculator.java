package com.smartbox.investory.investment.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/** Pure month-weighted rules for the market-investments income source. */
public final class InvestmentIncomeCalculator {
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private InvestmentIncomeCalculator() {}

  public static BigDecimal weightedIncomeBase(
      BigDecimal valueAtYearStart, List<MonthlyExternalFlow> flows) {
    BigDecimal result = nz(valueAtYearStart);
    if (flows == null) return result.setScale(8, RoundingMode.HALF_UP);
    for (MonthlyExternalFlow flow : flows) {
      if (flow == null || flow.month() == null) continue;
      int month = flow.month().getMonthValue();
      BigDecimal weight = BigDecimal.valueOf(13L - month).divide(TWELVE, 16, RoundingMode.HALF_UP);
      result = result.add(nz(flow.externalTransfer()).multiply(weight));
    }
    return result.setScale(8, RoundingMode.HALF_UP);
  }

  public static BigDecimal projectedAnnualInvestmentResult(
      BigDecimal incomeBase, BigDecimal expectedAnnualReturn) {
    return nz(incomeBase).multiply(nz(expectedAnnualReturn)).setScale(8, RoundingMode.HALF_UP);
  }

  public static BigDecimal expectedInvestmentResultYtd(
      BigDecimal expectedAnnualInvestmentResult, int currentMonth) {
    if (currentMonth < 1 || currentMonth > 12) return BigDecimal.ZERO.setScale(8);
    return nz(expectedAnnualInvestmentResult)
        .multiply(BigDecimal.valueOf(currentMonth))
        .divide(TWELVE, 8, RoundingMode.HALF_UP);
  }

  /** Zero is the deterministic, finite representation for an undefined expectation ratio. */
  public static BigDecimal expectationProgress(BigDecimal actual, BigDecimal expected) {
    if (expected == null || expected.signum() == 0) return BigDecimal.ZERO.setScale(8);
    return nz(actual).divide(expected, 8, RoundingMode.HALF_UP);
  }

  public record MonthlyExternalFlow(YearMonth month, BigDecimal externalTransfer) {}

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
