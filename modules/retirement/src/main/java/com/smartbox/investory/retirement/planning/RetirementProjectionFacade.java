package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.ProfileReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.time.Clock;
import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Prepares the single forward-projection context consumed by both retirement boards. */
@Service
public class RetirementProjectionFacade implements RetirementProjectionApi {
  private final ProfileReader profiles;
  private final RetirementPlanApi plans;
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulation simulations;
  private final Clock clock;

  public RetirementProjectionFacade(
      ProfileReader profiles,
      RetirementPlanApi plans,
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
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    return load(
        portfolioId, planId, defaultCurrentAge, defaultEndAge, SimulationCustomDeltas.zero());
  }

  public RetirementProjectionContext load(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      SimulationCustomDeltas customDeltas) {
    InvestmentProfile profile = profiles.loadProfile(portfolioId);
    var planDetails = planId == null ? null : plans.details(portfolioId, planId);
    SimulationAssumptions assumptions =
        planDetails == null
            ? SimulationAssumptions.defaults(
                profile,
                defaultCurrentAge == null ? 40 : defaultCurrentAge,
                defaultEndAge == null ? 95 : defaultEndAge,
                Year.now(clock).getValue())
            : planDetails.assumptions();
    return project(
        profile, assumptions, planDetails == null ? null : planDetails.baseline(), customDeltas);
  }

  public RetirementProjectionContext project(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return project(profile, assumptions, null);
  }

  /** Projects from a reviewed baseline without substituting newer live balances. */
  public RetirementProjectionContext project(
      InvestmentProfile profile, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    return project(profile, assumptions, baseline, SimulationCustomDeltas.zero());
  }

  public RetirementProjectionContext project(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      PlanningBaseline baseline,
      SimulationCustomDeltas customDeltas) {
    InvestmentProfile projectionProfile =
        baseline == null ? profile : PlanningProfileBaseline.apply(profile, baseline);
    ForwardSimulationInput forward = forwardInputs.prepare(projectionProfile, assumptions);
    SimulationAssumptions projectedAssumptions = forward.forwardAssumptions().orElse(assumptions);
    InvestmentProfile projectedProfile = forward.bridgedProfile();
    Map<SimulationScenario, SimulationResult> results =
        forward.forwardAssumptions().isPresent()
            ? (customDeltas == null || customDeltas.isZero()
                ? simulations.compareScenarios(
                    projectedProfile, projectedAssumptions, forward.context().asOfYear())
                : simulations.compareScenarios(
                    projectedProfile,
                    projectedAssumptions,
                    forward.context().asOfYear(),
                    customDeltas))
            : new EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, SimulationDecisionSummary> summaries =
        new EnumMap<>(SimulationScenario.class);
    results.forEach(
        (scenario, result) ->
            summaries.put(scenario, SimulationDecisionSummary.from(result, projectedAssumptions)));
    return new RetirementProjectionContext(
        profile, assumptions, forward, projectedProfile, projectedAssumptions, results, summaries);
  }
}
