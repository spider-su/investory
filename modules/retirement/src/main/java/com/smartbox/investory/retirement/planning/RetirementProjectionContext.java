package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationResult;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
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
