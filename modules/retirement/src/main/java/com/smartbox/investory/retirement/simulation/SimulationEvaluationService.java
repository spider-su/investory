package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
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
    SimulationResult result = simulations.simulate(profile, assumptions, scenario);
    SimulationDecisionSummary summary = SimulationDecisionSummary.from(result, assumptions);
    return new SimulationEvaluation(result, summary, PlanSustainabilityAssessment.from(summary));
  }
}
