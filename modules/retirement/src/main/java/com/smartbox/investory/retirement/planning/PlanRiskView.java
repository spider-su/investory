package com.smartbox.investory.retirement.planning;

import java.util.List;

/** Compact Simulation presentation split between external risks and planning levers. */
public record PlanRiskView(
    String interpretation,
    List<SimulationSensitivityAnalysisMoney.Driver> primaryRisks,
    List<SimulationSensitivityAnalysisMoney.Driver> allRisks,
    List<SimulationSensitivityAnalysisMoney.Driver> planningLevers) {
  public PlanRiskView {
    primaryRisks = List.copyOf(primaryRisks);
    allRisks = List.copyOf(allRisks);
    planningLevers = List.copyOf(planningLevers);
  }
}
