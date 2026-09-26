package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import com.smartbox.investory.retirement.api.model.SimulationResult;

/** UI boundary for the retirement sandbox calculation. */
public interface RetirementSandboxClient {
  SimulationResult simulate(Long portfolioId, SandboxSimulationInput input);
}
