package com.smartbox.investory.application.longterm;

import java.math.BigDecimal;

/** Canonical current planning metrics for one real-estate asset. */
public record RealEstatePlanningSummary(
    BigDecimal taxBase,
    BigDecimal annualTax,
    BigDecimal totalPaymentMonthly,
    BigDecimal monthlyIncome,
    BigDecimal monthlyReduce,
    BigDecimal incomeYield) {
  /** Current monthly income after annualized property, insurance, and rental-tax reductions. */
  public BigDecimal netMonthlyIncome() {
    return monthlyIncome.subtract(monthlyReduce);
  }
}
