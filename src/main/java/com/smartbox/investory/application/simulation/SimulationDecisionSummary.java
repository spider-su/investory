package com.smartbox.investory.application.simulation;

import java.math.BigDecimal;
import java.util.List;

/** Backend-derived decision metrics. Values are nominal and in the profile currency. */
public record SimulationDecisionSummary(
    SimulationScenario scenario,
    boolean failed,
    BigDecimal finalNetWorth,
    BigDecimal finalLiquidAssets,
    BigDecimal finalIlliquidAssets,
    BigDecimal minimumLiquidAssets,
    int minimumLiquidYear,
    int minimumLiquidAge,
    BigDecimal lowestNetWorth,
    int lowestNetWorthYear,
    int lowestNetWorthAge,
    BigDecimal lifetimeRequiredPortfolioFunding,
    BigDecimal lifetimeActualWithdrawals,
    Integer firstFailureYear,
    Integer firstFailureAge,
    BigDecimal totalUnfundedAmount,
    BigDecimal firstYearPassiveIncomeCoverage,
    BigDecimal minimumPassiveIncomeCoverage,
    BigDecimal totalEquityHarvested,
    BigDecimal totalEmergencyEquityWithdrawals,
    BigDecimal totalManualLiquidReserveWithdrawals,
    BigDecimal minimumManualLiquidReserve,
    BigDecimal minimumSafeReserveCoverageYears,
    int yearsWithEquityHarvest,
    int yearsUsingEmergencyEquity,
    BigDecimal finalSafeReserve,
    boolean recurringFundingGapRequired) {

  /** Compatibility constructor for callers that predate the reserve-applicability flag. */
  public SimulationDecisionSummary(
      SimulationScenario scenario,
      boolean failed,
      BigDecimal finalNetWorth,
      BigDecimal finalLiquidAssets,
      BigDecimal finalIlliquidAssets,
      BigDecimal minimumLiquidAssets,
      int minimumLiquidYear,
      int minimumLiquidAge,
      BigDecimal lowestNetWorth,
      int lowestNetWorthYear,
      int lowestNetWorthAge,
      BigDecimal lifetimeRequiredPortfolioFunding,
      BigDecimal lifetimeActualWithdrawals,
      Integer firstFailureYear,
      Integer firstFailureAge,
      BigDecimal totalUnfundedAmount,
      BigDecimal firstYearPassiveIncomeCoverage,
      BigDecimal minimumPassiveIncomeCoverage,
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
        finalLiquidAssets,
        finalIlliquidAssets,
        minimumLiquidAssets,
        minimumLiquidYear,
        minimumLiquidAge,
        lowestNetWorth,
        lowestNetWorthYear,
        lowestNetWorthAge,
        lifetimeRequiredPortfolioFunding,
        lifetimeActualWithdrawals,
        firstFailureYear,
        firstFailureAge,
        totalUnfundedAmount,
        firstYearPassiveIncomeCoverage,
        minimumPassiveIncomeCoverage,
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

  public static SimulationDecisionSummary from(
      SimulationResult result, SimulationAssumptions assumptions) {
    List<SimulationYear> years = result.years();
    if (years.isEmpty()) {
      return new SimulationDecisionSummary(
          result.scenario(),
          result.simulationFailed(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          assumptions.startYear(),
          assumptions.currentAge(),
          BigDecimal.ZERO,
          assumptions.startYear(),
          assumptions.currentAge(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          result.failureAge(),
          result.failureAge(),
          result.totalUnfundedAmount(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          0,
          0,
          BigDecimal.ZERO,
          false);
    }
    SimulationYear minLiquid =
        decisionYears(years).stream()
            .min((a, b) -> a.spendableAssetsEnd().compareTo(b.spendableAssetsEnd()))
            .orElseThrow();
    SimulationYear minNetWorth =
        years.stream().min((a, b) -> a.endNetWorth().compareTo(b.endNetWorth())).orElseThrow();
    SimulationYear firstFailure =
        years.stream().filter(SimulationYear::failed).findFirst().orElse(null);
    List<SimulationYear> retirementYears = decisionYears(years);
    BigDecimal firstCoverage = coverage(retirementYears.get(0));
    BigDecimal minCoverage =
        retirementYears.stream()
            .map(SimulationDecisionSummary::coverage)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    List<SimulationYear> reserveYears =
        retirementYears.stream().filter(year -> year.recurringFundingGap().signum() > 0).toList();
    BigDecimal minReserveCoverage =
        reserveYears.stream()
            .map(SimulationYear::safeReserveCoverageYears)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    return new SimulationDecisionSummary(
        result.scenario(),
        result.simulationFailed(),
        result.finalYear().endNetWorth(),
        result.finalYear().spendableAssetsEnd(),
        result.finalYear().totalIlliquidAssets(),
        minLiquid.spendableAssetsEnd(),
        year(assumptions, minLiquid),
        minLiquid.age(),
        minNetWorth.endNetWorth(),
        year(assumptions, minNetWorth),
        minNetWorth.age(),
        years.stream()
            .map(SimulationYear::requiredPortfolioFunding)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        years.stream()
            .map(SimulationYear::actualPortfolioWithdrawal)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        firstFailure == null ? null : year(assumptions, firstFailure),
        firstFailure == null ? null : firstFailure.age(),
        years.stream().map(SimulationYear::unfundedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
        firstCoverage,
        minCoverage,
        years.stream()
            .map(SimulationYear::equityToFixedIncomeTransfer)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        years.stream()
            .map(SimulationYear::emergencyEquityWithdrawal)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        years.stream()
            .map(SimulationYear::manualLiquidReserveWithdrawal)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        years.stream()
            .map(SimulationYear::manualLiquidReserveEnd)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO),
        minReserveCoverage,
        (int)
            years.stream().filter(year -> year.equityToFixedIncomeTransfer().signum() > 0).count(),
        (int) years.stream().filter(year -> year.emergencyEquityWithdrawal().signum() > 0).count(),
        result.finalYear().safeReserveEnd(),
        !reserveYears.isEmpty());
  }

  /** Primary withdrawal metric: only money actually funded from spendable assets. */
  public BigDecimal lifetimeWithdrawals() {
    return lifetimeActualWithdrawals;
  }

  private static int year(SimulationAssumptions assumptions, SimulationYear year) {
    return assumptions.startYear() + year.year();
  }

  private static BigDecimal coverage(SimulationYear year) {
    BigDecimal recurring = year.coreExpenses().add(year.discretionaryExpenses());
    return recurring.signum() == 0
        ? BigDecimal.ZERO
        : year.passiveIncome().divide(recurring, 8, java.math.RoundingMode.HALF_UP);
  }

  private static List<SimulationYear> decisionYears(List<SimulationYear> years) {
    List<SimulationYear> retired =
        years.stream()
            .filter(year -> year.lifecyclePhase() == SimulationLifecyclePhase.RETIRED)
            .toList();
    return retired.isEmpty() ? years : retired;
  }
}
