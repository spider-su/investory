package com.smartbox.investory.retirement.api.model;

/** Result bundle shared by deterministic analysis services. */
public record SimulationEvaluation(
    SimulationResult result,
    SimulationDecisionSummary decisionSummary,
    PlanSustainabilityAssessment sustainability) {
  public boolean sustainable() {
    return sustainability.sustainable();
  }
}
