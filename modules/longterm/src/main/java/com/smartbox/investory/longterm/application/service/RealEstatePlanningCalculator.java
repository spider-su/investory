package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Calculates current apartment planning metrics from effective cash-flow periods. */
public final class RealEstatePlanningCalculator {
  private static final BigDecimal TAX_RATE = new BigDecimal("0.085");
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  public BigDecimal annualRentalTax(BigDecimal taxBase) {
    return annualRentalTax(taxBase, TAX_RATE);
  }

  public BigDecimal annualRentalTax(BigDecimal taxBase, BigDecimal taxRate) {
    return (taxBase == null ? BigDecimal.ZERO : taxBase)
        .multiply(TWELVE)
        .multiply(taxRate == null ? TAX_RATE : taxRate);
  }

  public RealEstatePlanningSummary calculate(
      BigDecimal currentValue,
      BigDecimal taxBase,
      boolean assetTaxPaidByTenant,
      List<RentalContractModel> contracts,
      LocalDate date,
      BigDecimal taxRate) {
    var contract =
        contracts.stream()
            .filter(c -> c.startDate() != null && !c.startDate().isAfter(date))
            .filter(c -> c.endDate() == null || !c.endDate().isBefore(date))
            .filter(c -> c.terminatedDate() == null || !c.terminatedDate().isBefore(date))
            .max(Comparator.comparing(RentalContractModel::startDate))
            .orElse(null);
    if (contract == null)
      return new RealEstatePlanningSummary(
          taxBase,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    BigDecimal income = BigDecimal.ZERO, payment = BigDecimal.ZERO, expenses = BigDecimal.ZERO;
    for (var term : contract.terms()) {
      BigDecimal monthly =
          term.frequency() == Frequency.MONTHLY
              ? term.amount()
              : term.amount().divide(TWELVE, 18, RoundingMode.HALF_UP);
      BigDecimal annual =
          term.frequency() == Frequency.MONTHLY ? term.amount().multiply(TWELVE) : term.amount();
      if (isIncome(term.type())) income = income.add(monthly);
      // Payment is the recurring tenant turnover shown in planning. Annual landlord
      // costs are reductions, not part of the monthly payment amount.
      if (isPayment(term.type())
          && term.frequency() == Frequency.MONTHLY
          && (isIncome(term.type()) || term.paidByTenant())) payment = payment.add(monthly);
      if (isExpense(term.type()) && !term.paidByTenant()) expenses = expenses.add(annual);
    }
    boolean tenant =
        contract.rentalTaxPaidByTenant() == null
            ? assetTaxPaidByTenant
            : contract.rentalTaxPaidByTenant();
    BigDecimal effectiveTaxBase =
        contract.monthlyTaxBase() == null ? taxBase : contract.monthlyTaxBase();
    BigDecimal annualTax = tenant ? BigDecimal.ZERO : annualRentalTax(effectiveTaxBase, taxRate);
    expenses = normalizeMoney(expenses);
    BigDecimal reduce = expenses.add(annualTax).divide(TWELVE, 18, RoundingMode.HALF_UP);
    BigDecimal net = income.subtract(reduce);
    BigDecimal yield =
        currentValue.signum() == 0
            ? BigDecimal.ZERO
            : net.multiply(TWELVE).divide(currentValue, 12, RoundingMode.HALF_UP);
    return new RealEstatePlanningSummary(
        effectiveTaxBase, annualTax, payment, income, reduce, yield);
  }

  private static boolean isIncome(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.OTHER_INCOME;
  }

  private static boolean isPayment(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.ADMIN_FEE
        || type == CashFlowType.UTILITIES;
  }

  private static boolean isExpense(CashFlowType type) {
    return type == CashFlowType.ADMIN_FEE
        || type == CashFlowType.UTILITIES
        || type == CashFlowType.PROPERTY_TAX
        || type == CashFlowType.INSURANCE
        || type == CashFlowType.OTHER_EXPENSE;
  }

  private static BigDecimal normalizeMoney(BigDecimal value) {
    BigDecimal rounded = value.setScale(3, RoundingMode.HALF_UP);
    return rounded.stripTrailingZeros().scale() <= 0
        ? rounded.setScale(0, RoundingMode.UNNECESSARY)
        : rounded.stripTrailingZeros();
  }
}
