package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

/** Display-currency-only decision-summary amounts. Ratios and years are deliberately unchanged. */
public record SimulationDecisionSummaryMoney(
    SimulationScenario scenario,
    boolean failed,
    BigDecimal finalNetWorth,
    BigDecimal finalSpendableAssets,
    BigDecimal minimumSpendableAssets,
    BigDecimal lowestNetWorth,
    BigDecimal lifetimeActualWithdrawals,
    BigDecimal totalUnfundedAmount,
    BigDecimal firstYearPassiveIncomeCoverage,
    BigDecimal minimumPassiveIncomeCoverage,
    Integer firstFailureYear,
    Integer firstFailureAge,
    BigDecimal totalEquityHarvested,
    BigDecimal totalEmergencyEquityWithdrawals,
    BigDecimal totalManualLiquidReserveWithdrawals,
    BigDecimal minimumManualLiquidReserve,
    BigDecimal minimumSafeReserveCoverageYears,
    int yearsWithEquityHarvest,
    int yearsUsingEmergencyEquity,
    BigDecimal finalSafeReserve,
    boolean recurringFundingGapRequired) {
  public String finalNetWorthDisplay() {
    return PlanningPresentation.wholeNumber(finalNetWorth);
  }

  public String minimumSpendableAssetsDisplay() {
    return PlanningPresentation.wholeNumber(minimumSpendableAssets);
  }

  /** Canonical bucket-model name; the record field remains for API compatibility. */
  public BigDecimal minimumLiquidAssets() {
    return minimumSpendableAssets;
  }

  public String minimumLiquidAssetsDisplay() {
    return minimumSpendableAssets == null
        ? "—"
        : PlanningPresentation.compactMoney(minimumSpendableAssets);
  }

  /** Explains the minimum balance outcome; this is not a user-configured reserve threshold. */
  public String minimumLiquidAssetsContext() {
    if (minimumSpendableAssets == null) return "not available";
    return minimumSpendableAssets.signum() <= 0
        ? "liquid assets depleted"
        : "lowest projected balance";
  }

  public String finalSpendableAssetsDisplay() {
    return PlanningPresentation.wholeNumber(finalSpendableAssets);
  }

  public String lifetimeActualWithdrawalsDisplay() {
    return PlanningPresentation.wholeNumber(lifetimeActualWithdrawals);
  }

  public String minimumPassiveIncomeCoverageDisplay() {
    return PlanningPresentation.percentage(minimumPassiveIncomeCoverage);
  }

  public String minimumSafeReserveCoverageYearsDisplay() {
    return recurringFundingGapRequired
        ? PlanningPresentation.years(minimumSafeReserveCoverageYears)
        : "N/A";
  }
}
