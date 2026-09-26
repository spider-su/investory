package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import com.smartbox.investory.retirement.rest.RetirementSandboxRestController;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementSandboxClient implements RetirementSandboxClient {
  private final RetirementSandboxRestController rest;

  public InProcessRetirementSandboxClient(RetirementSandboxRestController rest) {
    this.rest = rest;
  }

  @Override
  public SimulationResult simulate(Long portfolioId, SandboxSimulationInput input) {
    return rest.simulate(portfolioId, input);
  }
}
