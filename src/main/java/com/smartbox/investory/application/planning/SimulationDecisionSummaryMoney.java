package com.smartbox.investory.application.planning;

import com.smartbox.investory.application.simulation.SimulationScenario;
import com.smartbox.investory.services.PlanningPresentation;
import java.math.BigDecimal;

/** Display-currency-only decision-summary amounts. Ratios and years are deliberately unchanged. */
public record SimulationDecisionSummaryMoney(
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

  public String finalNetWorthDisplay() {
    return PlanningPresentation.wholeNumber(finalNetWorth);
  }

  public String minimumSpendableAssetsDisplay() {
    return PlanningPresentation.wholeNumber(minimumSpendableAssets);
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
