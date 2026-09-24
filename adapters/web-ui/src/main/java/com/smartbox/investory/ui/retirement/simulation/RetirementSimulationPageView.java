package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Focused raw-projection model for the Simulation board. */
public record RetirementSimulationPageView(
    InvestmentProfile profile,
    SimulationAssumptions forwardAssumptions,
    CurrencyType displayCurrency,
    Long selectedPlanId,
    SimulationScenario selectedScenario,
    List<SimulationScenario> availableScenarios,
    List<ScenarioAssumptionView> scenarioAssumptionRows,
    SimulationDecisionSummaryMoney outlook,
    PlanningTimeline timeline,
    Map<Integer, PlanningTimelineMoney> timelineMoney,
    Map<Integer, RetirementYearSummaryView> yearlySummaries,
    PlanTimelineView planTimeline,
    RetirementSimulationChartView chartData) {
  public RetirementSimulationPageView {
    timelineMoney = Map.copyOf(timelineMoney);
    yearlySummaries = Collections.unmodifiableMap(new LinkedHashMap<>(yearlySummaries));
    scenarioAssumptionRows = List.copyOf(scenarioAssumptionRows);
  }
}
