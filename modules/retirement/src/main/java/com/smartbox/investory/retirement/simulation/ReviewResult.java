package com.smartbox.investory.retirement.simulation;

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
    actualIncome = nz(actualIncome);
    actualExpenses = nz(actualExpenses);
    projectedRemainingIncome = nz(projectedRemainingIncome);
    projectedRemainingExpenses = nz(projectedRemainingExpenses);
    expectedFullYearNetResult = nz(expectedFullYearNetResult);
    varianceFromOriginalPlan = nz(varianceFromOriginalPlan);
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
