package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.util.Map;

/** Canonical runtime result shared by simulation, timeline, analysis, and narrow consumers. */
public record RetirementProjection(
    InvestmentProfile profile,
    SimulationAssumptions assumptions,
    ForwardSimulationInput forward,
    InvestmentProfile projectedProfile,
    SimulationAssumptions projectedAssumptions,
    Map<SimulationScenario, SimulationResult> scenarioResults,
    Map<SimulationScenario, SimulationDecisionSummary> summaries) {
  public RetirementProjection {
    scenarioResults = Map.copyOf(scenarioResults);
    summaries = Map.copyOf(summaries);
  }
}
