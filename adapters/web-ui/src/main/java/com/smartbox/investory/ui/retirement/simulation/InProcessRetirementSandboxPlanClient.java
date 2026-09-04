package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementSandboxPlanApi;
import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementSandboxPlanClient implements RetirementSandboxPlanClient {
  private final RetirementSandboxPlanApi sandboxPlans;

  public InProcessRetirementSandboxPlanClient(
      @Qualifier("simulationPlanService") RetirementSandboxPlanApi sandboxPlans) {
    this.sandboxPlans = sandboxPlans;
  }

  @Override
  public Optional<Long> resolve(Long portfolioId) {
    return sandboxPlans.resolve(portfolioId);
  }

  @Override
  public SandboxSimulationInput load(Long portfolioId, Long sandboxPlanId) {
    return sandboxPlans.load(portfolioId, sandboxPlanId);
  }

  @Override
  public Long save(Long portfolioId, Long sandboxPlanId, SandboxSimulationInput input) {
    return sandboxPlans.save(portfolioId, sandboxPlanId, input);
  }
}
