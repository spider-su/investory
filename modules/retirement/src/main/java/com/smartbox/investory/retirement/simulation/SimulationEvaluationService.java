package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import org.springframework.stereotype.Service;

/** Thin boundary for analyses that repeatedly invoke the canonical simulator. */
@Service
public class SimulationEvaluationService {
  private final RetirementSimulation simulations;

  public SimulationEvaluationService(RetirementSimulation simulations) {
    this.simulations = simulations;
  }

  public SimulationEvaluation evaluate(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    return evaluate(profile, assumptions, scenario, assumptions.startYear());
  }

  public SimulationEvaluation evaluate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear) {
    return evaluate(profile, assumptions, scenario, baselineYear, null);
  }

  SimulationEvaluation evaluate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      SimulationEvaluationCache cache) {
    if (cache == null) return compute(profile, assumptions, scenario, baselineYear);
    return cache.getOrCompute(
        profile,
        assumptions,
        scenario,
        baselineYear,
        () -> compute(profile, assumptions, scenario, baselineYear));
  }

  private SimulationEvaluation compute(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear) {
    SimulationResult result = simulations.simulate(profile, assumptions, scenario, baselineYear);
    SimulationDecisionSummary summary = SimulationDecisionSummary.from(result, assumptions);
    return new SimulationEvaluation(result, summary, PlanSustainabilityAssessment.from(summary));
  }
}
