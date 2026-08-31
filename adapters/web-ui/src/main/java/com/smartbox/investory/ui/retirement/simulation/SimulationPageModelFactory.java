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
import java.util.List;
import java.util.Map;

/** Creates the complete Thymeleaf model for the simulation page. */
final class SimulationPageModelFactory {
  private SimulationPageModelFactory() {}

  static RetirementSimulationPageView create(
      InvestmentProfile profile,
      PlanningProfileMoney startingPosition,
      SimulationAssumptions assumptions,
      SimulationAssumptions projectedAssumptions,
      CurrencyType displayCurrency,
      Long selectedPlanId,
      String activePlanName,
      String activePlanSummary,
      SimulationScenario selectedScenario,
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
    return new RetirementSimulationPageView(
        profile,
        startingPosition,
        assumptions,
        projectedAssumptions,
        displayCurrency,
        selectedPlanId,
        activePlanName,
        activePlanSummary,
        selectedScenario,
        List.of(SimulationScenario.values()),
        scenarioAssumptions,
        scenarioAssumptionRows,
        customScenario,
        outlook,
        annualCosts,
        annualLivingExpenses,
        annualDiscretionaryExpenses,
        annualPension,
        timeline,
        timelineMoney,
        yearlySummaries,
        planTimeline,
        cashFlow,
        currentYearCloseAllowed,
        chartData);
  }
}
