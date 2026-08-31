package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.util.Objects;
import java.util.Optional;

/** Shared future-only inputs prepared once for a request. */
public record ForwardSimulationInput(
    ForwardSimulationContext context,
    InvestmentProfile bridgedProfile,
    Optional<SimulationAssumptions> forwardAssumptions,
    CurrentYearBridgeResult currentYearBridge) {
  public ForwardSimulationInput(
      ForwardSimulationContext context,
      InvestmentProfile bridgedProfile,
      Optional<SimulationAssumptions> forwardAssumptions) {
    this(context, bridgedProfile, forwardAssumptions, null);
  }

  public ForwardSimulationInput {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(bridgedProfile, "bridgedProfile");
    Objects.requireNonNull(forwardAssumptions, "forwardAssumptions");
  }
}
