package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record SimulationSensitivityResult(
    SensitivityDriver driver,
    String perturbationLabel,
    SimulationEvaluation baseline,
    SimulationEvaluation adverse,
    SimulationEvaluation favorable,
    BigDecimal reserveCoverageDelta,
    boolean adverseIntroducesRecurringFundingGap,
    BigDecimal spendableAssetsDelta,
    BigDecimal finalNetWorthDelta,
    SensitivityImpact impact,
    BigDecimal lowerTestedValue,
    BigDecimal baseTestedValue,
    BigDecimal higherTestedValue,
    SimulationEvaluation lowerEvaluation,
    SimulationEvaluation higherEvaluation,
    String moreHarmfulDirection) {

  /** Compatibility constructor for callers using the original harmful/favorable model. */
  public SimulationSensitivityResult(
      SensitivityDriver driver,
      String perturbationLabel,
      SimulationEvaluation baseline,
      SimulationEvaluation adverse,
      SimulationEvaluation favorable,
      BigDecimal reserveCoverageDelta,
      boolean adverseIntroducesRecurringFundingGap,
      BigDecimal spendableAssetsDelta,
      BigDecimal finalNetWorthDelta,
      SensitivityImpact impact) {
    this(
        driver,
        perturbationLabel,
        baseline,
        adverse,
        favorable,
        reserveCoverageDelta,
        adverseIntroducesRecurringFundingGap,
        spendableAssetsDelta,
        finalNetWorthDelta,
        impact,
        null,
        null,
        null,
        adverse,
        favorable,
        "Adverse");
  }

  public boolean adverseCausesFailure() {
    return baseline.sustainable() && !adverse.sustainable();
  }

  public boolean isWealthOnly() {
    return impact == SensitivityImpact.WEALTH_ONLY;
  }
}
