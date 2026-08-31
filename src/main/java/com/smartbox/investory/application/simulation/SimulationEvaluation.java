package com.smartbox.investory.application.simulation;

/** Result bundle shared by deterministic analysis services. */
public record SimulationEvaluation(
    SimulationResult result,
    SimulationDecisionSummary decisionSummary,
    PlanSustainabilityAssessment sustainability) {
  public boolean sustainable() {
    return sustainability.sustainable();
  }
}
