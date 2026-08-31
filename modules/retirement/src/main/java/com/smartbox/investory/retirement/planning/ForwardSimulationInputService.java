package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Prepares one bridged, rebased future boundary for all Simulation consumers. */
@Service
public class ForwardSimulationInputService {
  private final ForwardSimulationContextFactory contexts;
  private final CurrentYearProjectionBridge bridge;

  @Autowired
  public ForwardSimulationInputService(
      ForwardSimulationContextFactory contexts, CurrentYearProjectionBridge bridge) {
    this.contexts = contexts;
    this.bridge = bridge;
  }

  public ForwardSimulationInput prepare(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    var context = contexts.create(profile, assumptions);
    var currentYearBridge = bridge.projectCurrentYearEnd(context);
    return new ForwardSimulationInput(
        context,
        currentYearBridge.bridgedProfile(),
        context.forwardAssumptions(),
        currentYearBridge);
  }
}
