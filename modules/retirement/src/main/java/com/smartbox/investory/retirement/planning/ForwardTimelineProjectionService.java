package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.util.List;

/** Owns the current-to-future simulation seam used by the planning timeline. */
final class ForwardTimelineProjectionService {
  private final RetirementSimulation simulations;
  private final CurrentYearProjectionBridge projectionBridge;
  private final ForwardSimulationContextFactory contexts;

  ForwardTimelineProjectionService(
      RetirementSimulation simulations,
      CurrentYearProjectionBridge projectionBridge,
      ForwardSimulationContextFactory contexts) {
    this.simulations = simulations;
    this.projectionBridge = projectionBridge;
    this.contexts = contexts;
  }

  List<SimulationYear> future(
      InvestmentProfile profile, SimulationAssumptions assumptions, int current) {
    ForwardSimulationContext context = contexts.create(profile, assumptions);
    if (context.forwardAssumptions().isEmpty()) return List.of();
    return simulations
        .simulate(
            projectionBridge.projectCurrentYearEnd(profile, assumptions),
            context.forwardAssumptions().orElseThrow(),
            SimulationScenario.BASE,
            context.asOfYear())
        .years();
  }

  List<SimulationYear> future(
      ForwardSimulationInput forward, int current, SimulationScenario scenario) {
    if (forward.forwardAssumptions().isEmpty()) return List.of();
    return simulations
        .simulate(
            forward.bridgedProfile(),
            forward.forwardAssumptions().orElseThrow(),
            scenario,
            forward.context().asOfYear())
        .years();
  }
}
