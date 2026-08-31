package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.Frequency;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetCashFlow;
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
    BigDecimal payment = BigDecimal.ZERO;
    BigDecimal income = BigDecimal.ZERO;
    BigDecimal propertyTax = BigDecimal.ZERO;
    BigDecimal insurance = BigDecimal.ZERO;
    for (LongTermAssetCashFlow flow : flows) {
      if (!LongTermAssetCalculator.applies(flow, date)) continue;
      if (flow.getFrequency() == Frequency.MONTHLY) {
        if (isPayment(flow.getType())) payment = payment.add(flow.getAmount());
        if (isIncome(flow.getType())) income = income.add(flow.getAmount());
      } else if (flow.getType() == CashFlowType.PROPERTY_TAX)
        propertyTax = propertyTax.add(flow.getAmount());
      else if (flow.getType() == CashFlowType.INSURANCE)
        insurance = insurance.add(flow.getAmount());
    }
    BigDecimal annualTax = annualRentalTax(taxBase);
    BigDecimal reduce =
        propertyTax.add(insurance).add(annualTax).divide(TWELVE, 18, RoundingMode.HALF_UP);
    BigDecimal netIncome = income.subtract(reduce);
    BigDecimal yield =
        currentValue.signum() == 0
            ? BigDecimal.ZERO
            : netIncome.multiply(TWELVE).divide(currentValue, 12, RoundingMode.HALF_UP);
    return new RealEstatePlanningSummary(taxBase, annualTax, payment, income, reduce, yield);
  }

  public BigDecimal annualRentalTax(BigDecimal taxBase) {
    return (taxBase == null ? BigDecimal.ZERO : taxBase).multiply(TAX_RATE);
  }

  private static boolean isPayment(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.ADMIN_FEE
        || type == CashFlowType.UTILITIES;
  }

  private static boolean isIncome(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.OTHER_INCOME;
  }
}
