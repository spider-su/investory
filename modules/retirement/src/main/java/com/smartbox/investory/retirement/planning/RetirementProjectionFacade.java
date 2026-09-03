package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.ProfileComposition;
import com.smartbox.investory.profile.api.ProfilePlanningReader;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.ProfileSummaryReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.time.Clock;
import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Prepares the single forward-projection context consumed by both retirement boards. */
@Service
public class RetirementProjectionFacade implements RetirementProjectionApi {
  private final ProfileSummaryReader summaries;
  private final ProfilePlanningReader planning;
  private final ProfileSnapshotReader profileSnapshots;
  private final RetirementPlanApi plans;
  private final ForwardSimulationInputService forwardInputs;
  private final RetirementSimulation simulations;
  private final Clock clock;

  @Autowired
  public RetirementProjectionFacade(
      ProfileSummaryReader summaries,
      ProfilePlanningReader planning,
      ProfileSnapshotReader profileSnapshots,
      RetirementPlanApi plans,
      ForwardSimulationInputService forwardInputs,
      RetirementSimulation simulations,
      Clock clock) {
    this.summaries = summaries;
    this.planning = planning;
    this.profileSnapshots = profileSnapshots;
    this.plans = plans;
    this.forwardInputs = forwardInputs;
    this.simulations = simulations;
    this.clock = clock;
  }

  public RetirementProjectionFacade(
      ProfileSummaryReader summaries,
      ProfilePlanningReader planning,
      RetirementPlanApi plans,
      ForwardSimulationInputService forwardInputs,
      RetirementSimulation simulations,
      Clock clock) {
    this(summaries, planning, null, plans, forwardInputs, simulations, clock);
  }

  public RetirementProjectionContext load(Long portfolioId, Long planId) {
    return load(portfolioId, planId, 40, 95);
  }

  public RetirementProjectionContext load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    InvestmentProfile profile =
        profileSnapshots == null
            ? ProfileComposition.load(summaries, planning, portfolioId)
            : profileSnapshots.loadProfile(portfolioId);
    var planDetails = planId == null ? null : plans.details(portfolioId, planId);
    SimulationAssumptions assumptions =
        planDetails == null
            ? SimulationAssumptions.defaults(
                profile,
                defaultCurrentAge == null ? 40 : defaultCurrentAge,
                defaultEndAge == null ? 95 : defaultEndAge,
                Year.now(clock).getValue())
            : planDetails.assumptions();
    return project(profile, assumptions, planDetails == null ? null : planDetails.baseline());
  }

  public RetirementProjectionContext project(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return project(profile, assumptions, null);
  }

  /** Projects from a reviewed baseline without substituting newer live balances. */
  public RetirementProjectionContext project(
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
    return new RetirementProjectionContext(
        profile, assumptions, forward, projectedProfile, projectedAssumptions, results, summaries);
  }
}
