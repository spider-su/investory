package com.smartbox.investory.retirement.api.model;

import java.util.Objects;

/** One execution's resolved plan after applying optional transient sandbox assumptions. */
public record EffectiveRetirementPlan(
    RetirementFacts facts, SimulationAssumptions assumptions, PlanningBaseline baseline) {
  public EffectiveRetirementPlan {
    Objects.requireNonNull(facts, "facts");
    Objects.requireNonNull(assumptions, "assumptions");
  }

  public static EffectiveRetirementPlan resolve(
      RetirementFacts facts, RetirementPlan plan, SandboxOverrides overrides) {
    Objects.requireNonNull(plan, "plan");
    SimulationAssumptions assumptions =
        overrides == null || overrides.assumptions() == null
            ? plan.assumptions()
            : overrides.assumptions();
    return new EffectiveRetirementPlan(facts, assumptions, plan.baseline());
  }
}
