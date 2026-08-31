package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningProfileMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.ScenarioEffectiveAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
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
    CustomScenarioView customScenario,
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
