package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import com.smartbox.investory.retirement.api.model.SimulationResult;

/** Stateless retirement what-if calculation with explicit bucket inputs. */
public interface RetirementSandboxApi {
  SimulationResult simulate(SandboxSimulationInput input);
}
