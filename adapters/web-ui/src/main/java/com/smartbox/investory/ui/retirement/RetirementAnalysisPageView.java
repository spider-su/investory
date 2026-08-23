package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanRiskView;
import com.smartbox.investory.retirement.planning.PlanningFlexibilityMoney;
import com.smartbox.investory.retirement.planning.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.planning.SimulationScenarioComparison;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Locale;

/** Focused Analysis-board model. Values come from one completed simulation projection. */
public record RetirementAnalysisPageView(
    Long portfolioId,
    Long planId,
    String activePlanName,
    CurrencyType displayCurrency,
    SimulationScenario selectedScenario,
    SimulationDecisionSummaryMoney selectedSummary,
    SimulationScenarioComparison scenarios,
    PlanRiskView risks,
    PlanningFlexibilityMoney flexibility,
    SimulationChartData charts,
    String horizon) {
  public String selectedScenarioLabel() {
    String value = selectedScenario == null ? "" : selectedScenario.name().toLowerCase(Locale.ROOT);
    return value.isEmpty() ? "—" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
