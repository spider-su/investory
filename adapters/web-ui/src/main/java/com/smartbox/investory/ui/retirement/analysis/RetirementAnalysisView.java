package com.smartbox.investory.ui.retirement.analysis;

import com.smartbox.investory.retirement.api.model.PlanRiskView;
import com.smartbox.investory.retirement.api.model.PlanningFlexibilityMoney;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import java.util.Map;

/** UI-owned analysis result after transport and backend presentation mapping. */
public record RetirementAnalysisView(
    boolean available,
    Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries,
    PlanRiskView displayRisk,
    PlanningFlexibilityMoney displayFlexibility,
    SimulationChartData displayCharts) {}
