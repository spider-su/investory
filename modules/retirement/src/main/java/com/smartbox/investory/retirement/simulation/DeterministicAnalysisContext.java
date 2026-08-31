package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.util.Objects;

/** Frozen inputs and canonical Base result shared by deterministic Analysis calculations. */
public record DeterministicAnalysisContext(
    InvestmentProfile profile,
    SimulationAssumptions assumptions,
    int baselineYear,
    SimulationEvaluation canonicalBase,
    SimulationEvaluationCache cache) {
  public DeterministicAnalysisContext(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      int baselineYear,
      SimulationEvaluation canonicalBase) {
    this(profile, assumptions, baselineYear, canonicalBase, new SimulationEvaluationCache());
  }

  public DeterministicAnalysisContext {
    Objects.requireNonNull(profile, "Analysis profile is required");
    Objects.requireNonNull(assumptions, "Analysis assumptions are required");
    Objects.requireNonNull(canonicalBase, "Canonical Base evaluation is required");
    Objects.requireNonNull(cache, "Analysis evaluation cache is required");
    if (canonicalBase.result().scenario() != SimulationScenario.BASE
        || canonicalBase.decisionSummary().scenario() != SimulationScenario.BASE)
      throw new IllegalArgumentException("Canonical Analysis result must be Base");
  }
}
