package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.SimulationScenario;
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
  /** Compatibility constructor for display callers that predate reserve applicability. */
  public SimulationDecisionSummaryMoney(
      SimulationScenario scenario,
      boolean failed,
      BigDecimal finalNetWorth,
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
      BigDecimal finalSafeReserve) {
    this(
        scenario,
        failed,
        finalNetWorth,
        minimumSpendableAssets,
        minimumSpendableAssets,
        lowestNetWorth,
        lifetimeActualWithdrawals,
        totalUnfundedAmount,
        firstYearPassiveIncomeCoverage,
        minimumPassiveIncomeCoverage,
        firstFailureYear,
        firstFailureAge,
        totalEquityHarvested,
        totalEmergencyEquityWithdrawals,
        totalManualLiquidReserveWithdrawals,
        minimumManualLiquidReserve,
        minimumSafeReserveCoverageYears,
        yearsWithEquityHarvest,
        yearsUsingEmergencyEquity,
        finalSafeReserve,
        true);
  }

  public SimulationDecisionSummaryMoney(
      SimulationScenario scenario,
      boolean failed,
      BigDecimal finalNetWorth,
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
    this(
        scenario,
        failed,
        finalNetWorth,
        minimumSpendableAssets,
        minimumSpendableAssets,
        lowestNetWorth,
        lifetimeActualWithdrawals,
        totalUnfundedAmount,
        firstYearPassiveIncomeCoverage,
        minimumPassiveIncomeCoverage,
        firstFailureYear,
        firstFailureAge,
        totalEquityHarvested,
        totalEmergencyEquityWithdrawals,
        totalManualLiquidReserveWithdrawals,
        minimumManualLiquidReserve,
        minimumSafeReserveCoverageYears,
        yearsWithEquityHarvest,
        yearsUsingEmergencyEquity,
        finalSafeReserve,
        recurringFundingGapRequired);
  }

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
    return PlanningPresentation.wholeNumber(minimumSpendableAssets);
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
