package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Loads and assembles the model for the simulation page. */
@Component
final class SimulationPageAssembler {
  private final RetirementPlanClient plans;
  private final RetirementTimelineClient planningTimeline;
  private final RetirementProjectionClient projections;
  private final Clock clock;
  private final ScenarioObservationService observations;

  private static final List<SimulationScenario> AVAILABLE_SCENARIOS =
      List.of(
          SimulationScenario.CONSERVATIVE, SimulationScenario.BASE, SimulationScenario.OPTIMISTIC);

  SimulationPageAssembler(
      RetirementPlanClient plans,
      RetirementTimelineClient planningTimeline,
      RetirementProjectionClient projections,
      Clock clock,
      ScenarioObservationService observations) {
    this.plans = plans;
    this.planningTimeline = planningTimeline;
    this.projections = projections;
    this.clock = clock;
    this.observations = observations;
  }

  RetirementSimulationPageView assemble(Long portfolioId, SimulationQuery query) {
    int currentYear = Year.now(clock).getValue();
    CurrencyType currency = query.getPlanningDisplayCurrency();
    SimulationScenario scenario = query.getSelectedScenario();
    // Calendar progression is planning-state maintenance only. It must happen before the
    // timeline query so the page never presents a stale current/historical boundary.
    planningTimeline.ensurePlanningTimeline(portfolioId);
    Long planId = plans.resolvePlanId(portfolioId, query.getPlanId()).orElse(null);
    var displayProjection = projections.loadDisplay(portfolioId, planId, null, null, currency);
    var projection = displayProjection.projection();
    var projected = projection.projectedAssumptions();
    var timelineResponse =
        planningTimeline.loadForwardTimeline(portfolioId, projection, scenario, currency);
    var timeline = timelineResponse.timeline();
    var timelineMoney = timelineResponse.money();
    var yearly = RetirementYearSummaryView.from(timeline, timelineMoney);
    var toDisplay =
        (java.util.function.Function<BigDecimal, BigDecimal>)
            amount -> displayMoney(amount, currency);
    var chart = RetirementSimulationChartView.from(timeline, timelineMoney, projected);
    var planTimeline =
        PlanTimelineView.from(
            timeline,
            yearly,
            timelineMoney,
            projected,
            chart.retirementYear(),
            chart.pensionStartYear(),
            currentYear,
            toDisplay);
    var scenarioValues =
        ScenarioEffectiveAssumptions.forScenario(
            projection.projectedProfile(),
            projected,
            scenario,
            projection.forward().context().asOfYear());
    Map<String, ScenarioObservation> loadedObservations = observations.load(portfolioId, timeline);
    var assumptionRows =
        List.of(
            assumption(
                "Inflation",
                projected.inflationRate(),
                scenarioValues.inflationRate(),
                false,
                loadedObservations),
            assumption(
                "Rental growth",
                projected.effectiveRentalIncomeGrowthRate(),
                scenarioValues.rentalIncomeGrowthRate(),
                true,
                loadedObservations),
            assumption(
                "Bond return",
                scenarioValues.planBondReturnRate(),
                scenarioValues.bondReturnRate(),
                true,
                loadedObservations),
            assumption(
                "Equity return",
                projected.equityReturnRate(),
                scenarioValues.equityReturnRate(),
                true,
                loadedObservations),
            assumption(
                "Spending growth",
                projected.effectiveSpendingGrowthRate(),
                scenarioValues.spendingGrowthRate(),
                false,
                loadedObservations));
    return new RetirementSimulationPageView(
        projection.profile(),
        projected,
        currency,
        planId,
        scenario,
        AVAILABLE_SCENARIOS,
        assumptionRows,
        displayProjection.summaries().get(scenario),
        timeline,
        timelineMoney,
        yearly,
        planTimeline,
        chart);
  }

  private BigDecimal displayMoney(BigDecimal amount, CurrencyType currency) {
    return amount.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
  }

  private ScenarioAssumptionView assumption(
      String name,
      BigDecimal plan,
      BigDecimal effective,
      boolean higherIsBetter,
      Map<String, ScenarioObservation> loaded) {
    var observation = loaded.getOrDefault(name, ScenarioObservation.unavailable());
    return ScenarioAssumptionView.of(
        name,
        plan,
        effective,
        higherIsBetter,
        observation.value(),
        observation.label(),
        observation.period(),
        observation.availability());
  }
}
