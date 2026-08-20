package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.Frequency;
import com.smartbox.investory.longterm.infrastructure.LongTermAssetCashFlow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Calculates current apartment planning metrics from effective cash-flow periods. */
public final class RealEstatePlanningCalculator {
  private static final BigDecimal TAX_RATE = new BigDecimal("0.085");
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  public RealEstatePlanningSummary calculate(
      BigDecimal currentValue,
      BigDecimal taxBase,
      List<LongTermAssetCashFlow> flows,
      LocalDate date) {
    return calculate(currentValue, taxBase, flows, date, TAX_RATE, false);
  }

  public RealEstatePlanningSummary calculate(
      BigDecimal currentValue,
      BigDecimal taxBase,
      List<LongTermAssetCashFlow> flows,
      LocalDate date,
      BigDecimal taxRate,
      boolean rentalTaxPaidByTenant) {
    BigDecimal payment = BigDecimal.ZERO;
    BigDecimal income = BigDecimal.ZERO;
    BigDecimal landlordExpenses = BigDecimal.ZERO;
    for (LongTermAssetCashFlow flow : flows) {
      if (!LongTermAssetCalculator.applies(flow, date)) continue;
      BigDecimal monthly =
          flow.getFrequency() == Frequency.MONTHLY
              ? flow.getAmount()
              : flow.getAmount().divide(TWELVE, 18, RoundingMode.HALF_UP);
      BigDecimal annual =
          flow.getFrequency() == Frequency.MONTHLY
              ? flow.getAmount().multiply(TWELVE)
              : flow.getAmount();
      if (isIncome(flow.getType())) income = income.add(monthly);
      if (isPayment(flow.getType()) && (!isExpense(flow.getType()) || flow.isPaidByTenant()))
        payment = payment.add(monthly);
      if (isExpense(flow.getType()) && !flow.isPaidByTenant())
        landlordExpenses = landlordExpenses.add(annual);
    }
    BigDecimal annualTax =
        rentalTaxPaidByTenant ? BigDecimal.ZERO : annualRentalTax(taxBase, taxRate);
    BigDecimal reduce = landlordExpenses.add(annualTax).divide(TWELVE, 18, RoundingMode.HALF_UP);
    BigDecimal netIncome = income.subtract(reduce);
    BigDecimal yield =
        currentValue.signum() == 0
            ? BigDecimal.ZERO
            : netIncome.multiply(TWELVE).divide(currentValue, 12, RoundingMode.HALF_UP);
    return new RealEstatePlanningSummary(taxBase, annualTax, payment, income, reduce, yield);
  }

  public BigDecimal annualRentalTax(BigDecimal taxBase) {
    return annualRentalTax(taxBase, TAX_RATE);
  }

  public BigDecimal annualRentalTax(BigDecimal taxBase, BigDecimal taxRate) {
    return (taxBase == null ? BigDecimal.ZERO : taxBase)
        .multiply(taxRate == null ? TAX_RATE : taxRate);
  }

  private static boolean isPayment(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.ADMIN_FEE
        || type == CashFlowType.UTILITIES
        || isExpense(type);
  }

  private static boolean isIncome(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.OTHER_INCOME;
  }

  private static boolean isExpense(CashFlowType type) {
    return type == CashFlowType.ADMIN_FEE
        || type == CashFlowType.UTILITIES
        || type == CashFlowType.PROPERTY_TAX
        || type == CashFlowType.INSURANCE
        || type == CashFlowType.OTHER_EXPENSE;
  }
}
