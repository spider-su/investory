package com.smartbox.investory.retirement.api.model;

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

  public boolean adverseCausesFailure() {
    return baseline.sustainable() && !adverse.sustainable();
  }

  public boolean isWealthOnly() {
    return impact == SensitivityImpact.WEALTH_ONLY;
  }
}
