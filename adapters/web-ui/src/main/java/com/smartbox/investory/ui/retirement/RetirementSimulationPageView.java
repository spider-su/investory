package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningProfileMoney;
import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    SimulationDecisionSummaryMoney outlook,
    String annualLivingExpenses,
    String annualDiscretionaryExpenses,
    String annualPension,
    PlanningTimeline timeline,
    Map<Integer, PlanningTimelineMoney> timelineMoney,
    Map<Integer, RetirementYearSummaryView> yearlySummaries,
    CashFlowSectionView cashFlow,
    boolean currentYearCloseAllowed) {
  public RetirementSimulationPageView {
    timelineMoney = Map.copyOf(timelineMoney);
    yearlySummaries = Collections.unmodifiableMap(new LinkedHashMap<>(yearlySummaries));
  }
}
