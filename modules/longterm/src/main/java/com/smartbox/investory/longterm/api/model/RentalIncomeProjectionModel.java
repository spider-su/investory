package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

/** Canonical rental projection. A contract, not an individual term, is the baseline. */
public final class RentalIncomeProjectionModel {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
  private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.085");

  private RentalIncomeProjectionModel() {}

  public static Result project(
      LongTermAssetProjectionModel asset,
      Map<CashFlowTypeModel, BigDecimal> previousIncome,
      int year,
      BigDecimal growthRate) {
    // Forward planning is assumption-based, not a replay of contract termination. The latest
    // known contract remains the baseline after its historical end/termination until a newer
    // contract starts; a newer contract (including an explicit zero-rent contract) replaces it.
    // actualYear below is the separate historical/actual path and always honors real dates.
    // Compatibility-only fallback for legacy/bootstrap planning inputs with untyped periods.
    // Normal persisted real-estate runtime projections use rentalContracts.
    if (asset.rentalContracts().isEmpty())
      return legacyProject(asset, previousIncome, year, growthRate);
    var contract = latestContract(asset, year);
    if (contract == null) return new Result(Map.of(), ZERO, ZERO, ZERO, ZERO);
    EnumMap<CashFlowTypeModel, BigDecimal> income = new EnumMap<>(CashFlowTypeModel.class);
    for (var term : contract.terms()) {
      if (!isIncome(term.type())) continue;
      BigDecimal base = annual(term.amount(), term.frequency());
      BigDecimal prior = previousIncome.get(term.type());
      income.put(
          term.type(),
          prior == null || contract.startDate().getYear() == year ? base : grow(prior, growthRate));
    }
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal expenses =
        contract.terms().stream()
            .filter(t -> isExpense(t.type()) && !t.paidByTenant())
            .map(t -> annual(t.amount(), t.frequency()))
            .reduce(ZERO, BigDecimal::add);
    return result(income, gross, expenses, tax(asset, contract));
  }

  private static Result legacyProject(
      LongTermAssetProjectionModel asset,
      Map<CashFlowTypeModel, BigDecimal> previousIncome,
      int year,
      BigDecimal growthRate) {
    EnumMap<CashFlowTypeModel, BigDecimal> income = new EnumMap<>(CashFlowTypeModel.class);
    for (CashFlowTypeModel type :
        new CashFlowTypeModel[] {
          CashFlowTypeModel.RENT, CashFlowTypeModel.PARKING_RENT, CashFlowTypeModel.OTHER_INCOME
        }) {
      var period =
          asset.periods().stream()
              .filter(p -> p.cashFlowType() == type)
              .filter(p -> p.validFrom().getYear() <= year)
              .max(Comparator.comparing(LongTermAssetProjectionModel.Period::validFrom));
      if (period.isEmpty()) continue;
      BigDecimal base = period.get().annualIncome();
      BigDecimal prior = previousIncome.get(type);
      income.put(
          type,
          prior == null || period.get().validFrom().getYear() == year
              ? base
              : grow(prior, growthRate));
    }
    BigDecimal expenses =
        asset.periods().stream()
            .filter(
                p ->
                    p.cashFlowType() != null
                        && !p.paidByTenant()
                        && (isExpense(p.cashFlowType()) || isIncome(p.cashFlowType())))
            .map(LongTermAssetProjectionModel.Period::annualExpense)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    return result(
        income,
        gross,
        expenses,
        asset.rentalTaxPaidByTenant() ? ZERO : taxBase(asset).multiply(rate(asset)));
  }

  /** Calculates covered rental economics. Rental tax is prorated by covered calendar days. */
  public static Result actualYear(LongTermAssetProjectionModel asset, int year) {
    // Retained only for legacy/bootstrap snapshots; contract-backed runtime data follows the
    // date-aware calculation below.
    if (asset.rentalContracts().isEmpty()) return legacyActualYear(asset, year);
    EnumMap<CashFlowTypeModel, BigDecimal> income = new EnumMap<>(CashFlowTypeModel.class);
    BigDecimal expenses = ZERO, tax = ZERO;
    for (var contract : asset.rentalContracts()) {
      BigDecimal covered = coverage(contract.startDate(), effectiveEnd(contract), year);
      if (covered.signum() == 0) continue;
      for (var term : contract.terms()) {
        BigDecimal amount = annual(term.amount(), term.frequency()).multiply(covered);
        if (isIncome(term.type())) income.merge(term.type(), amount, BigDecimal::add);
        if (isExpense(term.type()) && !term.paidByTenant()) expenses = expenses.add(amount);
      }
      tax = tax.add(tax(asset, contract).multiply(covered));
    }
    return result(income, income.values().stream().reduce(ZERO, BigDecimal::add), expenses, tax);
  }

  private static Result legacyActualYear(LongTermAssetProjectionModel asset, int year) {
    EnumMap<CashFlowTypeModel, BigDecimal> income = new EnumMap<>(CashFlowTypeModel.class);
    BigDecimal expenses = ZERO;
    for (var p : asset.periods()) {
      BigDecimal covered = coverage(p.validFrom(), p.validTo(), year);
      if (covered.signum() == 0 || p.cashFlowType() == null) continue;
      if (isIncome(p.cashFlowType()))
        income.merge(p.cashFlowType(), p.annualIncome().multiply(covered), BigDecimal::add);
      if (isIncome(p.cashFlowType()) && !p.paidByTenant())
        expenses = expenses.add(p.annualExpense().multiply(covered));
      if (isExpense(p.cashFlowType()) && !p.paidByTenant())
        expenses = expenses.add(p.annualExpense().multiply(covered));
    }
    BigDecimal gross = income.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal tax = asset.rentalTaxPaidByTenant() ? ZERO : taxBase(asset).multiply(rate(asset));
    return result(income, gross, expenses, tax);
  }

  private static RentalContractModel latestContract(LongTermAssetProjectionModel asset, int year) {
    LocalDate horizon = LocalDate.of(year, 12, 31);
    return asset.rentalContracts().stream()
        .filter(c -> !c.startDate().isAfter(horizon))
        .max(Comparator.comparing(RentalContractModel::startDate))
        .orElse(null);
  }

  private static BigDecimal tax(LongTermAssetProjectionModel asset, RentalContractModel contract) {
    boolean tenant =
        contract.rentalTaxPaidByTenant() == null
            ? asset.rentalTaxPaidByTenant()
            : contract.rentalTaxPaidByTenant();
    return tenant ? ZERO : taxBase(asset).multiply(rate(asset));
  }

  private static BigDecimal taxBase(LongTermAssetProjectionModel asset) {
    return asset.taxBase() == null ? ZERO : asset.taxBase();
  }

  private static BigDecimal rate(LongTermAssetProjectionModel asset) {
    return asset.taxRate() == null ? DEFAULT_TAX_RATE : asset.taxRate();
  }

  private static BigDecimal annual(BigDecimal amount, FrequencyModel frequency) {
    return frequency == FrequencyModel.MONTHLY ? amount.multiply(TWELVE) : amount;
  }

  private static BigDecimal coverage(LocalDate from, LocalDate to, int year) {
    LocalDate yearStart = LocalDate.of(year, 1, 1), yearEnd = LocalDate.of(year, 12, 31);
    LocalDate start = from.isAfter(yearStart) ? from : yearStart,
        end = to == null || to.isAfter(yearEnd) ? yearEnd : to;
    if (start.isAfter(end)) return ZERO;
    return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end.plusDays(1)))
        .divide(BigDecimal.valueOf(Year.of(year).length()), 18, RoundingMode.HALF_UP);
  }

  private static LocalDate effectiveEnd(RentalContractModel c) {
    return c.endDate() == null
        ? c.terminatedDate()
        : c.terminatedDate() == null || c.endDate().isBefore(c.terminatedDate())
            ? c.endDate()
            : c.terminatedDate();
  }

  private static boolean isIncome(CashFlowTypeModel t) {
    return t == CashFlowTypeModel.RENT
        || t == CashFlowTypeModel.PARKING_RENT
        || t == CashFlowTypeModel.OTHER_INCOME;
  }

  private static boolean isExpense(CashFlowTypeModel t) {
    return t == CashFlowTypeModel.ADMIN_FEE
        || t == CashFlowTypeModel.UTILITIES
        || t == CashFlowTypeModel.INSURANCE
        || t == CashFlowTypeModel.PROPERTY_TAX
        || t == CashFlowTypeModel.OTHER_EXPENSE;
  }

  private static BigDecimal grow(BigDecimal value, BigDecimal rate) {
    return value.multiply(BigDecimal.ONE.add(rate)).setScale(8, RoundingMode.HALF_UP);
  }

  private static Result result(
      Map<CashFlowTypeModel, BigDecimal> income,
      BigDecimal gross,
      BigDecimal expenses,
      BigDecimal tax) {
    var e = RentalEconomicsModel.of(gross, expenses, tax);
    return new Result(income, e.grossIncome(), e.expenses(), e.tax(), e.netIncome());
  }

  public record Result(
      Map<CashFlowTypeModel, BigDecimal> incomeByType,
      BigDecimal grossIncome,
      BigDecimal expenses,
      BigDecimal tax,
      BigDecimal netIncome) {
    public Result {
      incomeByType = Map.copyOf(incomeByType);
    }
  }
}
