package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Finds the earliest sustainable integer retirement age using the canonical simulator. */
@Service
public class RetirementAgeAnalysisService {
  private final SimulationEvaluationService evaluations;

  public RetirementAgeAnalysisService(SimulationEvaluationService evaluations) {
    this.evaluations = evaluations;
  }

  public RetirementAgeAnalysis analyze(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return new RetirementAgeAnalysis(
        find(profile, assumptions, SimulationScenario.BASE),
        find(profile, assumptions, SimulationScenario.CONSERVATIVE));
  }

  public RetirementAgeAnalysis analyze(DeterministicAnalysisContext context) {
    return new RetirementAgeAnalysis(
        find(context, SimulationScenario.BASE),
        find(context, SimulationScenario.CONSERVATIVE));
  }

  private RetirementAgeAnalysis.ScenarioResult find(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    return find(profile, assumptions, scenario, null);
  }

  private RetirementAgeAnalysis.ScenarioResult find(
      DeterministicAnalysisContext context, SimulationScenario scenario) {
    return find(context.profile(), context.assumptions(), scenario, context);
  }

  private RetirementAgeAnalysis.ScenarioResult find(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      DeterministicAnalysisContext context) {
    int planned = assumptions.retirementAge();
    Map<Integer, Boolean> outcomes = new LinkedHashMap<>();
    for (int age = assumptions.currentAge(); age <= assumptions.endAge(); age++)
      outcomes.put(age, evaluate(profile, assumptions, scenario, age, context));
    List<Integer> ages = new ArrayList<>(outcomes.keySet());
    boolean plannedSustainable =
        planned <= assumptions.currentAge()
            ? outcomes.getOrDefault(assumptions.currentAge(), false)
            : outcomes.getOrDefault(planned, false);
    Integer earliest =
        outcomes.entrySet().stream()
            .filter(entry -> entry.getValue())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    Integer earlierSustainable =
        outcomes.entrySet().stream()
            .filter(entry -> entry.getKey() < planned && entry.getValue())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    Integer laterSustainable =
        outcomes.entrySet().stream()
            .filter(entry -> entry.getKey() > planned && entry.getValue())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);

    int plannedYear = calendarYear(assumptions, planned);
    Integer earliestYear = earliest == null ? null : calendarYear(assumptions, earliest);
    RetirementTimingResultState state =
        state(
            assumptions,
            planned,
            plannedSustainable,
            earlierSustainable,
            laterSustainable,
            earliest);
    int headroom = earliest == null ? 0 : planned - earliest;
    int delay = earliest == null ? 0 : Math.max(0, earliest - planned);
    return new RetirementAgeAnalysis.ScenarioResult(
        scenario,
        planned,
        plannedYear,
        plannedSustainable,
        earliest,
        earliestYear,
        headroom,
        delay,
        state,
        outcomes.size(),
        List.copyOf(ages));
  }

  private boolean evaluate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int retirementAge,
      DeterministicAnalysisContext context) {
    if (context != null
        && scenario == SimulationScenario.BASE
        && retirementAge == assumptions.retirementAge())
      return context.canonicalBase().sustainable();
    SimulationAssumptions candidate = assumptions.withRetirementAge(retirementAge);
    return (context == null
            ? evaluations.evaluate(profile, candidate, scenario)
            : evaluations.evaluate(profile, candidate, scenario, context.baselineYear()))
        .sustainable();
  }

  private static RetirementTimingResultState state(
      SimulationAssumptions assumptions,
      int planned,
      boolean plannedSustainable,
      Integer earlierSustainable,
      Integer laterSustainable,
      Integer earliest) {
    if (earliest == null) return RetirementTimingResultState.NO_SUSTAINABLE_AGE;
    if (planned <= assumptions.currentAge() && plannedSustainable)
      return RetirementTimingResultState.ALREADY_RETIRED;
    if (!plannedSustainable && earlierSustainable != null)
      return RetirementTimingResultState.NON_MONOTONIC_RESULT;
    if (earliest == assumptions.currentAge())
      return RetirementTimingResultState.IMMEDIATE_RETIREMENT_AVAILABLE;
    if (earlierSustainable != null && plannedSustainable)
      return RetirementTimingResultState.EARLIER_RETIREMENT_AVAILABLE;
    if (!plannedSustainable && laterSustainable != null)
      return RetirementTimingResultState.DELAY_REQUIRED;
    return RetirementTimingResultState.PLANNED_AGE_IS_BOUNDARY;
  }

  private static int calendarYear(SimulationAssumptions assumptions, int age) {
    return assumptions.startYear() + age - assumptions.currentAge();
  }
}
