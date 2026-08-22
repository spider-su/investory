package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.planning.RetirementAnalysisService;
import com.smartbox.investory.retirement.planning.RetirementProjectionFacade;
import com.smartbox.investory.retirement.planning.SimulationScenarioComparison;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only interpretation board over the canonical forward-projection context. */
@Controller
public class RetirementAnalysisController {
  private final RetirementProjectionFacade projections;
  private final RetirementAnalysisService analyses;
  private final PlanningCurrencyPresentationService presentation;
  private final SimulationPlanService plans;

  public RetirementAnalysisController(
      RetirementProjectionFacade projections,
      RetirementAnalysisService analyses,
      PlanningCurrencyPresentationService presentation,
      SimulationPlanService plans) {
    this.projections = projections;
    this.analyses = analyses;
    this.presentation = presentation;
    this.plans = plans;
  }

  @GetMapping("/analysis")
  public String analysis(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    var projection = projections.load(portfolioId, selectedPlanId);
    var result = analyses.analyze(projection);
    var displaySummaries =
        new LinkedHashMap<>(
            presentation.displaySummaries(projection.summaries(), planningDisplayCurrency));
    var page =
        new RetirementAnalysisPageView(
            portfolioId,
            selectedPlanId,
            selectedPlanId == null ? "Current assumptions" : plans.name(portfolioId, selectedPlanId),
            planningDisplayCurrency,
            selectedScenario,
            displaySummaries.get(selectedScenario),
            SimulationScenarioComparison.from(
                projection.summaries(), displaySummaries, selectedScenario),
            presentation.displayPlanRisks(result.sensitivity(), planningDisplayCurrency),
            presentation.displayPlanningFlexibility(
                result.sustainableSpending(), result.retirementAge(), planningDisplayCurrency),
            presentation.displayCharts(result.charts(), planningDisplayCurrency),
            projection.projectedAssumptions().currentAge()
                + " → "
                + projection.projectedAssumptions().endAge());
    model.addAttribute("analysisPage", page);
    return "retirement-analysis";
  }
}
