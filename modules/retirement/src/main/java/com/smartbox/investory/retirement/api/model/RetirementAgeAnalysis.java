package com.smartbox.investory.retirement.api.model;

import java.util.List;

/** Deterministic annual retirement-age boundary for the configured plan horizon. */
public record RetirementAgeAnalysis(ScenarioResult base, ScenarioResult conservative) {
  public record ScenarioResult(
      SimulationScenario scenario,
      int plannedRetirementAge,
      int plannedRetirementYear,
      boolean plannedRetirementSustainable,
      Integer earliestSustainableRetirementAge,
      Integer earliestSustainableRetirementYear,
      int headroomYears,
      int delayYears,
      RetirementTimingResultState state,
      int evaluationCount,
      List<Integer> evaluatedAges) {}
}
