package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/** Projects canonical real-estate rental economics from Long-term Asset cash-flow periods. */
public final class RentalIncomeProjection {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private RentalIncomeProjection() {}

  public static Result project(
      LongTermAssetProjection asset,
      Map<CashFlowType, BigDecimal> previousIncome,
      int year,
      BigDecimal growthRate) {
    EnumMap<CashFlowType, BigDecimal> income = new EnumMap<>(CashFlowType.class);
    for (CashFlowType type :
        new CashFlowType[] {
          CashFlowType.RENT, CashFlowType.PARKING_RENT, CashFlowType.OTHER_INCOME
        }) {
      var active =
          asset.periods().stream()
              .filter(period -> period.cashFlowType() == type && applies(period, year))
              .max(java.util.Comparator.comparing(LongTermAssetProjection.Period::validFrom));
      if (active.isEmpty()) continue;
      var period = active.get();
      BigDecimal prior = previousIncome.get(type);
      income.put(
          type,
          prior == null || period.validFrom().getYear() == year
              ? period.annualIncome()
              : grow(prior, growthRate));
    }
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal expenses =
        asset.periods().stream()
            .filter(period -> applies(period, year))
            .map(LongTermAssetProjection.Period::annualExpense)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal tax =
        OptionalValue.orZero(asset.taxBase()).multiply(OptionalValue.orZero(asset.taxRate()));
    return new Result(income, gross, expenses, tax, gross.subtract(expenses).subtract(tax));
  }

  private static boolean applies(LongTermAssetProjection.Period period, int year) {
    return period.validFrom().getYear() <= year
        && (period.validTo() == null || period.validTo().getYear() >= year);
  }

  private static BigDecimal grow(BigDecimal value, BigDecimal rate) {
    return value.multiply(BigDecimal.ONE.add(rate)).setScale(8, RoundingMode.HALF_UP);
  }

  public record Result(
      Map<CashFlowType, BigDecimal> incomeByType,
      BigDecimal grossIncome,
      BigDecimal expenses,
      BigDecimal tax,
      BigDecimal netIncome) {
    public Result {
      incomeByType = Map.copyOf(incomeByType);
    }
  }

  private static final class OptionalValue {
    private OptionalValue() {}

    static BigDecimal orZero(BigDecimal value) {
      return value == null ? ZERO : value;
    }
  }
}
