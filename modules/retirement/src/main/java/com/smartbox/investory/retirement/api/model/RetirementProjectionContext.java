package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.util.Map;

/** One canonical projection boundary shared by the Simulation and Analysis boards. */
public record RetirementProjectionContext(
    InvestmentProfile profile,
    SimulationAssumptions assumptions,
    ForwardSimulationInput forward,
    InvestmentProfile projectedProfile,
    SimulationAssumptions projectedAssumptions,
    Map<SimulationScenario, SimulationResult> scenarioResults,
    Map<SimulationScenario, SimulationDecisionSummary> summaries) {
  public RetirementProjectionContext {
    scenarioResults = Map.copyOf(scenarioResults);
    summaries = Map.copyOf(summaries);
  }
}
