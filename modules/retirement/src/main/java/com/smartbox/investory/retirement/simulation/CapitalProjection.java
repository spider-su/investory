package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record CapitalProjection(
    int year,
    BigDecimal startValue,
    BigDecimal annualIncome,
    BigDecimal annualReturn,
    BigDecimal availableForWithdrawal,
    BigDecimal requestedWithdrawal,
    BigDecimal actualWithdrawal,
    BigDecimal endValue,
    ProjectionSource source) {
  public CapitalProjection {
    startValue = nz(startValue);
    annualIncome = nz(annualIncome);
    annualReturn = nz(annualReturn);
    availableForWithdrawal = nz(availableForWithdrawal).max(BigDecimal.ZERO);
    requestedWithdrawal = nz(requestedWithdrawal).max(BigDecimal.ZERO);
    actualWithdrawal = nz(actualWithdrawal).max(BigDecimal.ZERO)
        .min(availableForWithdrawal).min(startValue.add(annualReturn).max(BigDecimal.ZERO));
    endValue = nz(endValue).max(BigDecimal.ZERO);
    source = source == null ? ProjectionSource.PROJECTED : source;
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
