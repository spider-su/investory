package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

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
    for (CashFlowType type : INCOME_TYPES) {
      Optional<LongTermAssetProjection.Period> latest = latestKnown(asset, type, year);
      if (latest.isEmpty()) continue;
      var period = latest.get();
      BigDecimal prior = previousIncome.get(type);
      income.put(
          type,
          prior == null || period.validFrom().getYear() == year
              ? period.annualIncome()
              : grow(prior, growthRate));
    }
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal expenses = ZERO;
    for (CashFlowType type : INCOME_TYPES)
      expenses =
          expenses.add(
              latestKnown(asset, type, year)
                  .map(LongTermAssetProjection.Period::annualExpense)
                  .orElse(ZERO));
    for (CashFlowType type : EXPENSE_TYPES)
      expenses =
          expenses.add(
              latestKnown(asset, type, year)
                  .map(LongTermAssetProjection.Period::annualExpense)
                  .orElse(ZERO));
    BigDecimal tax =
        OptionalValue.orZero(asset.taxBase()).multiply(OptionalValue.orZero(asset.taxRate()));
    RentalEconomics economics = RentalEconomics.of(gross, expenses, tax);
    return new Result(
        income,
        economics.grossIncome(),
        economics.expenses(),
        economics.tax(),
        economics.netIncome());
  }

  /** Calculates effective-dated economics actually covered by a calendar year. */
  public static Result actualYear(LongTermAssetProjection asset, int year) {
    EnumMap<CashFlowType, BigDecimal> income = new EnumMap<>(CashFlowType.class);
    BigDecimal expenses = ZERO;
    for (LongTermAssetProjection.Period period : asset.periods()) {
      BigDecimal covered = coverage(period, year);
      if (covered.signum() == 0) continue;
      if (isIncome(period.cashFlowType())) {
        income.merge(
            period.cashFlowType(), period.annualIncome().multiply(covered), BigDecimal::add);
        expenses = expenses.add(period.annualExpense().multiply(covered));
      } else if (isExpense(period.cashFlowType()))
        expenses = expenses.add(period.annualExpense().multiply(covered));
    }
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal tax =
        OptionalValue.orZero(asset.taxBase()).multiply(OptionalValue.orZero(asset.taxRate()));
    RentalEconomics economics = RentalEconomics.of(gross, expenses, tax);
    return new Result(
        income,
        economics.grossIncome(),
        economics.expenses(),
        economics.tax(),
        economics.netIncome());
  }

  private static final CashFlowType[] INCOME_TYPES = {
    CashFlowType.RENT, CashFlowType.PARKING_RENT, CashFlowType.OTHER_INCOME
  };

  private static final CashFlowType[] EXPENSE_TYPES = {
    CashFlowType.ADMIN_FEE,
    CashFlowType.UTILITIES,
    CashFlowType.INSURANCE,
    CashFlowType.PROPERTY_TAX,
    CashFlowType.OTHER_EXPENSE
  };

  private static Optional<LongTermAssetProjection.Period> latestKnown(
      LongTermAssetProjection asset, CashFlowType type, int year) {
    return asset.periods().stream()
        .filter(period -> period.cashFlowType() == type)
        .filter(period -> period.validFrom().getYear() <= year)
        .max(java.util.Comparator.comparing(LongTermAssetProjection.Period::validFrom));
  }

  private static BigDecimal coverage(LongTermAssetProjection.Period period, int year) {
    LocalDate start = LocalDate.of(year, 1, 1);
    LocalDate end = LocalDate.of(year, 12, 31);
    LocalDate from = period.validFrom().isAfter(start) ? period.validFrom() : start;
    LocalDate to =
        period.validTo() == null || period.validTo().isAfter(end) ? end : period.validTo();
    if (from.isAfter(to)) return ZERO;
    long coveredDays = ChronoUnit.DAYS.between(from, to.plusDays(1));
    return BigDecimal.valueOf(coveredDays)
        .divide(BigDecimal.valueOf(Year.of(year).length()), 18, RoundingMode.HALF_UP);
  }

  private static boolean isIncome(CashFlowType type) {
    for (CashFlowType incomeType : INCOME_TYPES) if (incomeType == type) return true;
    return false;
  }

  private static boolean isExpense(CashFlowType type) {
    for (CashFlowType expenseType : EXPENSE_TYPES) if (expenseType == type) return true;
    return false;
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
