package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import java.util.Optional;

/** Persistence boundary for saved, portfolio-owned Sandbox scenarios. */
public interface RetirementSandboxPlanApi {
  Optional<Long> resolve(Long portfolioId);

  SandboxSimulationInput load(Long portfolioId, Long sandboxPlanId);

  Long save(Long portfolioId, Long sandboxPlanId, SandboxSimulationInput input);
}
