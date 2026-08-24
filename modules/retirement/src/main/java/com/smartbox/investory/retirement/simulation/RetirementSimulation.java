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

  default SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      SimulationAnnualPaths annualPaths) {
    return simulate(profile, assumptions, scenario, baselineYear);
  }

  default SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      SimulationCustomDeltas custom) {
    return simulate(profile, assumptions, scenario, baselineYear);
  }

  Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions);

  Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions, int baselineYear);

  default Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      int baselineYear,
      SimulationCustomDeltas custom) {
    return compareScenarios(profile, assumptions, baselineYear);
  }
}
