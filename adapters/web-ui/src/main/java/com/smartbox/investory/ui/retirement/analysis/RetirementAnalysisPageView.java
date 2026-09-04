package com.smartbox.investory.ui.retirement.analysis;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanRiskView;
import com.smartbox.investory.retirement.api.model.PlanningFlexibilityMoney;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationScenarioComparison;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Locale;

/** Focused Analysis-board model. Values come from one completed simulation projection. */
public record RetirementAnalysisPageView(
    Long portfolioId,
    Long planId,
    String activePlanName,
    CurrencyType displayCurrency,
    SimulationScenario selectedScenario,
    boolean analysisAvailable,
    String analysisMessage,
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
