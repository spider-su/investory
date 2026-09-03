package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Loads and assembles the model for the simulation page. */
@Component
final class SimulationPageAssembler {
  private final RetirementPlanClient plans;
  private final RetirementTimelineClient planningTimeline;
  private final RetirementPresentationClient presentation;
  private final RetirementProjectionClient projections;
  private final Clock clock;
  private final SimulationValueFormatter values;
  private final SimulationRequestMapper requests;
  private final ScenarioObservationService observations;

  SimulationPageAssembler(
      RetirementPlanClient plans,
      RetirementTimelineClient planningTimeline,
      RetirementPresentationClient presentation,
      RetirementProjectionClient projections,
      Clock clock,
      SimulationRequestMapper requests,
      ScenarioObservationService observations) {
    this.plans = plans;
    this.planningTimeline = planningTimeline;
    this.presentation = presentation;
    this.projections = projections;
    this.clock = clock;
    this.values = new SimulationValueFormatter(presentation);
    this.requests = requests;
    this.observations = observations;
  }

  RetirementSimulationPageView assemble(SimulationQuery query) {
    int currentYear = Year.now(clock).getValue();
    int currentAge = query.getCurrentAge() == null ? 40 : query.getCurrentAge();
    int endAge = query.getEndAge() == null ? 95 : query.getEndAge();
    Long portfolioId = query.getPortfolioId();
    CurrencyType currency = query.getPlanningDisplayCurrency();
    SimulationScenario requestedScenario = query.getSelectedScenario();
    SimulationScenario scenario = requestedScenario;
    Long planId = plans.resolvePlanId(portfolioId, query.getPlanId()).orElse(null);
    PlanDetails plan = planId == null ? null : plans.details(portfolioId, planId);
    var input = projections.load(portfolioId, planId, currentAge, endAge);
    var assumptions = requests.applyLegacyOverrides(input.assumptions(), query.legacyOverrides());
    var projection =
        projections.project(input.profile(), assumptions, plan == null ? null : plan.baseline());
    var projected = projection.projectedAssumptions();
    var timeline =
        planningTimeline.loadForwardTimeline(
            portfolioId, input.profile(), projection.forward(), scenario);
    var timelineMoney = presentation.displayTimelineMoney(timeline, currency, projected);
    var yearly = RetirementYearSummaryView.from(timeline, timelineMoney);
    var toDisplay =
        (java.util.function.Function<BigDecimal, BigDecimal>)
            amount -> values.money(amount, currency);
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
    var money =
        new java.util.function.Function<BigDecimal, BigDecimal>() {
          @Override
          public BigDecimal apply(BigDecimal amount) {
            return values.money(amount, currency);
          }
        };
    var cashFlow = CashFlowSectionView.from(timeline, timelineMoney, projected, money);
    var activeName = planId == null ? "Current assumptions" : plan.name();
    var activeSummary =
        projected.currentAge()
            + "–"
            + projected.endAge()
            + " · Retire at "
            + projected.retirementAge()
            + " · Effective cost growth "
            + PlanningPresentation.percentage(projected.effectiveSpendingGrowthRate());
    return SimulationPageModelFactory.create(
        input.profile(),
        presentation.displayProfile(input.profile(), currency),
        assumptions,
        projected,
        currency,
        planId,
        activeName,
        activeSummary,
        scenario,
        scenarioValues,
        assumptionRows,
        new LinkedHashMap<>(presentation.displaySummaries(projection.summaries(), currency))
            .get(scenario),
        values.money(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            currency),
        values.money(assumptions.annualLivingExpenses(), currency),
        values.money(assumptions.annualDiscretionaryExpenses(), currency),
        values.money(assumptions.annualPension(), currency),
        timeline,
        timelineMoney,
        yearly,
        planTimeline,
        cashFlow,
        timeline.years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .anyMatch(row -> row.year() < currentYear),
        chart);
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
