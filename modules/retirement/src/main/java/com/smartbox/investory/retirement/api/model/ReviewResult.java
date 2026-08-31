package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import java.math.BigDecimal;

public record ReviewResult(
    BigDecimal actualIncome,
    BigDecimal actualExpenses,
    BigDecimal projectedRemainingIncome,
    BigDecimal projectedRemainingExpenses,
    BigDecimal expectedFullYearNetResult,
    BigDecimal varianceFromOriginalPlan,
    ReserveState reserve) {
  public ReviewResult {
    actualIncome = zeroIfNull(actualIncome);
    actualExpenses = zeroIfNull(actualExpenses);
    projectedRemainingIncome = zeroIfNull(projectedRemainingIncome);
    projectedRemainingExpenses = zeroIfNull(projectedRemainingExpenses);
    expectedFullYearNetResult = zeroIfNull(expectedFullYearNetResult);
    varianceFromOriginalPlan = zeroIfNull(varianceFromOriginalPlan);
  }
}
