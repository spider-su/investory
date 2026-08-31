package com.smartbox.investory.retirement.simulation;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

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
    startValue = zeroIfNull(startValue);
    annualIncome = zeroIfNull(annualIncome);
    annualReturn = zeroIfNull(annualReturn);
    availableForWithdrawal = zeroIfNull(availableForWithdrawal).max(BigDecimal.ZERO);
    requestedWithdrawal = zeroIfNull(requestedWithdrawal).max(BigDecimal.ZERO);
    actualWithdrawal =
        zeroIfNull(actualWithdrawal).max(BigDecimal.ZERO).min(availableForWithdrawal);
    endValue = zeroIfNull(endValue).max(BigDecimal.ZERO);
    source = source == null ? ProjectionSource.PROJECTED : source;
  }
}
