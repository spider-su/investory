package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.util.Map;

/** Application boundary for annual retirement projections. */
public interface RetirementSimulation {
  SimulationResult simulate(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario);

  SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear);

  Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions);
}
