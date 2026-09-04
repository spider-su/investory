package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
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

  /** Projects only the first planning year using a fraction of recurring flows and returns. */
  SimulationYear simulateRemainingYear(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      java.math.BigDecimal recurringFraction);

  Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions);

  Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions, int baselineYear);
}
