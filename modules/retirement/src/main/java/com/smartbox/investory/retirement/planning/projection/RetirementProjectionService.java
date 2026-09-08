package com.smartbox.investory.retirement.planning.projection;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementFactsProvider;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.application.PlanningProfileBaseline;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single application execution boundary for current-year bridging and future retirement projection.
 */
@Service
public class RetirementProjectionService implements RetirementProjectionApi {
  private final RetirementFactsProvider factsProvider;
  private final RetirementPlanApi plans;
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulation simulations;
  private final Clock clock;

  @Autowired
  public RetirementProjectionService(
      RetirementFactsProvider factsProvider,
      RetirementPlanApi plans,
      ForwardSimulationInputService forwardInputs,
      RetirementSimulation simulations,
      Clock clock) {
    this.factsProvider = factsProvider;
    this.plans = plans;
    this.forwardInputs = forwardInputs;
    this.simulations = simulations;
    this.clock = clock;
  }

  @Override
  public RetirementProjection load(Long portfolioId, Long planId) {
    return load(portfolioId, planId, 40, 95);
  }

  @Override
  public RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    RetirementFacts facts = factsProvider.load(portfolioId);
    InvestmentProfile profile = facts.profile();
    var planDetails = planId == null ? null : plans.details(portfolioId, planId);
    SimulationAssumptions assumptions =
        planDetails == null
            ? SimulationAssumptions.defaults(
                defaultCurrentAge == null ? 40 : defaultCurrentAge,
                defaultEndAge == null ? 95 : defaultEndAge,
                facts.asOfYear())
            : planDetails.assumptions();
    RetirementPlan plan =
        new RetirementPlan(assumptions, planDetails == null ? null : planDetails.baseline());
    return project(facts, plan, null);
  }

  @Override
  public RetirementProjection project(
      InvestmentProfile profile, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    InvestmentProfile projectionProfile =
        baseline == null ? profile : PlanningProfileBaseline.apply(profile, baseline);
    ForwardSimulationInput forward = forwardInputs.prepare(projectionProfile, assumptions);
    SimulationAssumptions projectedAssumptions = forward.forwardAssumptions().orElse(assumptions);
    InvestmentProfile projectedProfile = forward.bridgedProfile();
    Map<SimulationScenario, SimulationResult> results =
        forward.forwardAssumptions().isPresent()
            ? simulations.compareScenarios(
                projectedProfile, projectedAssumptions, forward.context().asOfYear())
            : new EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, SimulationDecisionSummary> summaries =
        new EnumMap<>(SimulationScenario.class);
    results.forEach(
        (scenario, result) ->
            summaries.put(scenario, SimulationDecisionSummary.from(result, projectedAssumptions)));
    return new RetirementProjection(
        profile, assumptions, forward, projectedProfile, projectedAssumptions, results, summaries);
  }

  public RetirementProjection project(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return project(profile, assumptions, null);
  }

  /** Canonical facts -> effective plan -> bridge -> simulation entry point. */
  public RetirementProjection project(
      Long portfolioId, RetirementPlan plan, SandboxOverrides overrides) {
    RetirementFacts facts = factsProvider.load(portfolioId);
    return project(facts, plan, overrides);
  }

  private RetirementProjection project(
      RetirementFacts facts, RetirementPlan plan, SandboxOverrides overrides) {
    EffectiveRetirementPlan effective = EffectiveRetirementPlan.resolve(facts, plan, overrides);
    return project(effective.facts().profile(), effective.assumptions(), effective.baseline());
  }

  public CurrentYearSpendingProjection currentYearSpending(Long portfolioId, Long planId) {
    RetirementProjection projection = load(portfolioId, planId);
    var bridge = projection.forward().currentYearBridge();
    return new CurrentYearSpendingProjection(
        bridge.asOfYear(), bridge.retirementSpendingApplied(), planId);
  }

  /** Sandbox inputs already describe the simulation boundary; do not bridge the live year again. */
  public RetirementProjection projectSandbox(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    var context =
        new com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory(clock)
            .create(profile, assumptions);
    var forward =
        new ForwardSimulationInput(context, profile, java.util.Optional.of(assumptions), null);
    var results = simulations.compareScenarios(profile, assumptions);
    Map<SimulationScenario, SimulationDecisionSummary> summaries =
        new EnumMap<>(SimulationScenario.class);
    results.forEach(
        (scenario, result) ->
            summaries.put(scenario, SimulationDecisionSummary.from(result, assumptions)));
    return new RetirementProjection(
        profile, assumptions, forward, profile, assumptions, results, summaries);
  }
}
