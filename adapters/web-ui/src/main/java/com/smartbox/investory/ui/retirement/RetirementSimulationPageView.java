package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningProfileMoney;
import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.ScenarioEffectiveAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Focused raw-projection model for the Simulation board. */
public record RetirementSimulationPageView(
    InvestmentProfile profile,
    PlanningProfileMoney startingPosition,
    SimulationAssumptions assumptions,
    SimulationAssumptions forwardAssumptions,
    CurrencyType displayCurrency,
    Long selectedPlanId,
    String activePlanName,
    String activePlanSummary,
    SimulationScenario selectedScenario,
    List<SimulationScenario> availableScenarios,
    ScenarioEffectiveAssumptions scenarioAssumptions,
    List<ScenarioAssumptionView> scenarioAssumptionRows,
    SimulationDecisionSummaryMoney outlook,
    BigDecimal annualCosts,
    BigDecimal annualLivingExpenses,
    BigDecimal annualDiscretionaryExpenses,
    BigDecimal annualPension,
    PlanningTimeline timeline,
    Map<Integer, PlanningTimelineMoney> timelineMoney,
    Map<Integer, RetirementYearSummaryView> yearlySummaries,
    PlanTimelineView planTimeline,
    CashFlowSectionView cashFlow,
    boolean currentYearCloseAllowed,
    RetirementSimulationChartView chartData) {
  public RetirementSimulationPageView {
    timelineMoney = Map.copyOf(timelineMoney);
    yearlySummaries = Collections.unmodifiableMap(new LinkedHashMap<>(yearlySummaries));
    scenarioAssumptionRows = List.copyOf(scenarioAssumptionRows);
  }
}
