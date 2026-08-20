package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Canonical interpretation of one deterministic simulation result. */
public record PlanSustainabilityAssessment(
    PlanSustainabilityStatus status,
    Integer firstFailureYear,
    Integer firstFailureAge,
    BigDecimal totalUnfundedAmount,
    BigDecimal minimumSafeReserveCoverageYears,
    BigDecimal minimumSpendableAssets,
    BigDecimal finalNetWorth,
    boolean recurringFundingGapRequired) {

  /**
   * Compatibility constructor for synthetic assessments created before reserve applicability was
   * explicit.
   */
  public PlanSustainabilityAssessment(
      PlanSustainabilityStatus status,
      Integer firstFailureYear,
      Integer firstFailureAge,
      BigDecimal totalUnfundedAmount,
      BigDecimal minimumSafeReserveCoverageYears,
      BigDecimal minimumSpendableAssets,
      BigDecimal finalNetWorth) {
    this(
        status,
        firstFailureYear,
        firstFailureAge,
        totalUnfundedAmount,
        minimumSafeReserveCoverageYears,
        minimumSpendableAssets,
        finalNetWorth,
        true);
  }

  public boolean sustainable() {
    return status == PlanSustainabilityStatus.SUSTAINABLE;
  }

  public static PlanSustainabilityAssessment from(SimulationDecisionSummary summary) {
    return new PlanSustainabilityAssessment(
        summary.failed()
            ? PlanSustainabilityStatus.UNSUSTAINABLE
            : PlanSustainabilityStatus.SUSTAINABLE,
        summary.firstFailureYear(),
        summary.firstFailureAge(),
        summary.totalUnfundedAmount(),
        summary.minimumSafeReserveCoverageYears(),
        summary.minimumLiquidAssets(),
        summary.finalNetWorth(),
        summary.recurringFundingGapRequired());
  }
}
