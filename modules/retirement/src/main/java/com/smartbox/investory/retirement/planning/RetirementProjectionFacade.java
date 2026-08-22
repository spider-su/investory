package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.SimulationResult;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import java.time.Clock;
import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Prepares the single forward-projection context consumed by both retirement boards. */
@Service
public class RetirementProjectionFacade {
  private final InvestmentProfileFacade profiles;
  private final SimulationPlanService plans;
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulation simulations;
  private final Clock clock;

  public RetirementProjectionFacade(
      InvestmentProfileFacade profiles,
      SimulationPlanService plans,
      ForwardSimulationInputService forwardInputs,
      RetirementSimulation simulations,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.forwardInputs = forwardInputs;
    this.simulations = simulations;
    this.clock = clock;
  }

  public RetirementProjectionContext load(Long portfolioId, Long planId) {
    return load(portfolioId, planId, 40, 95);
  }

  public RetirementProjectionContext load(
      Long portfolioId, Long planId, int defaultCurrentAge, int defaultEndAge) {
    RetirementProjectionInput input = loadInput(portfolioId, planId, defaultCurrentAge, defaultEndAge);
    return project(input.profile(), input.assumptions());
  }

  public RetirementProjectionInput loadInput(
      Long portfolioId, Long planId, int defaultCurrentAge, int defaultEndAge) {
    InvestmentProfile profile = profiles.loadProfile(portfolioId);
    SimulationAssumptions assumptions =
        planId == null
            ? SimulationAssumptions.defaults(
                profile, defaultCurrentAge, defaultEndAge, Year.now(clock).getValue())
            : plans.assumptions(portfolioId, planId);
    return new RetirementProjectionInput(profile, assumptions);
  }

  public RetirementProjectionContext project(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    ForwardSimulationInput forward = forwardInputs.prepare(profile, assumptions);
    SimulationAssumptions projectedAssumptions = forward.forwardAssumptions().orElse(assumptions);
    InvestmentProfile projectedProfile = forward.bridgedProfile();
    Map<SimulationScenario, SimulationResult> results =
        forward.forwardAssumptions().isPresent()
            ? simulations.compareScenarios(projectedProfile, projectedAssumptions)
            : new EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, SimulationDecisionSummary> summaries =
        new EnumMap<>(SimulationScenario.class);
    results.forEach(
        (scenario, result) ->
            summaries.put(scenario, SimulationDecisionSummary.from(result, projectedAssumptions)));
    return new RetirementProjectionContext(
        profile,
        assumptions,
        forward,
        projectedProfile,
        projectedAssumptions,
        results,
        summaries);
  }
}
