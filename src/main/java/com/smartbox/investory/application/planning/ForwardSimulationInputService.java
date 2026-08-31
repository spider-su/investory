package com.smartbox.investory.application.planning;

import com.smartbox.investory.application.profile.InvestmentProfile;
import com.smartbox.investory.application.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.application.simulation.SimulationAssumptions;
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
    var bridged = bridge.projectCurrentYearEnd(context).bridgedProfile();
    return new ForwardSimulationInput(context, bridged, context.forwardAssumptions());
  }
}
