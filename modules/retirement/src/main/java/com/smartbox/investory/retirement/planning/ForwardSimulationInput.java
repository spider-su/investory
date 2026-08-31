package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContext;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
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
    if (context == null || bridgedProfile == null || forwardAssumptions == null)
      throw new IllegalArgumentException("Forward simulation input requires complete values");
  }
}
