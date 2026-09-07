package com.smartbox.investory.retirement.api.model;

import java.util.Objects;

/** Persisted user intent and assumptions, separated from live source facts. */
public record RetirementPlan(SimulationAssumptions assumptions, PlanningBaseline baseline) {
  public RetirementPlan {
    Objects.requireNonNull(assumptions, "assumptions");
  }

  public static RetirementPlan of(SimulationAssumptions assumptions) {
    return new RetirementPlan(assumptions, null);
  }
}
