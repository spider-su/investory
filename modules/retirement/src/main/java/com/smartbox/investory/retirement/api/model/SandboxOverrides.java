package com.smartbox.investory.retirement.api.model;

/** Transient what-if assumptions. Never persisted as source facts or as a plan revision. */
public record SandboxOverrides(SimulationAssumptions assumptions) {
  public static final SandboxOverrides NONE = new SandboxOverrides(null);
}
