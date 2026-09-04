package com.smartbox.investory.ui.retirement.analysis;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationScenarioComparison;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.ui.retirement.simulation.RetirementPlanClient;
import com.smartbox.investory.ui.retirement.simulation.RetirementPresentationClient;
import com.smartbox.investory.ui.retirement.simulation.RetirementProjectionClient;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only interpretation board over the canonical forward-projection context. */
@Controller
public class RetirementAnalysisController {
  private final RetirementProjectionClient projections;
  private final RetirementAnalysisClient analyses;
  private final RetirementPresentationClient presentation;
  private final RetirementPlanClient plans;

  @org.springframework.beans.factory.annotation.Autowired private PortfolioContextReader portfolios;

  public RetirementAnalysisController(
      RetirementProjectionClient projections,
      RetirementAnalysisClient analyses,
      RetirementPresentationClient presentation,
      RetirementPlanClient plans) {
    this.projections = projections;
    this.analyses = analyses;
    this.presentation = presentation;
    this.plans = plans;
  }

  @GetMapping("/analysis")
  public String analysis(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    var selectedPlan = selectedPlanId == null ? null : plans.details(portfolioId, selectedPlanId);
    var projection = projections.load(portfolioId, selectedPlanId, 40, 95);
    var result = analyses.analyze(projection);
    SimulationScenario displayedScenario = selectedScenario;
    var displaySummaries =
        new LinkedHashMap<>(
            presentation.displaySummaries(projection.summaries(), planningDisplayCurrency));
    var page =
        new RetirementAnalysisPageView(
            portfolioId,
            selectedPlanId,
            selectedPlanId == null ? "Current assumptions" : selectedPlan.name(),
            planningDisplayCurrency,
            displayedScenario,
            result.available(),
            result.available()
                ? null
                : "No future planning years remain after the current-year bridge.",
            displaySummaries.get(displayedScenario),
            SimulationScenarioComparison.from(
                projection.summaries(), displaySummaries, displayedScenario),
            result.available()
                ? presentation.displayPlanRisks(
                    result.sensitivity().value().orElseThrow(), planningDisplayCurrency)
                : null,
            result.available()
                ? presentation.displayPlanningFlexibility(
                    result.sustainableSpending().value().orElseThrow(),
                    result.retirementAge().value().orElseThrow(),
                    planningDisplayCurrency)
                : null,
            presentation.displayCharts(result.charts(), planningDisplayCurrency),
            result.available()
                ? projection.projectedAssumptions().currentAge()
                    + "–"
                    + projection.projectedAssumptions().endAge()
                : "No future years");
    model.addAttribute("analysisPage", page);
    return "retirement-analysis";
  }

  private CurrencyType resolveCurrency(Long portfolioId, CurrencyType requested) {
    if (requested != null) return requested;
    if (portfolios == null) return CurrencyType.PLN;
    return portfolios
        .findById(portfolioId)
        .map(context -> context.localCurrency())
        .orElse(CurrencyType.PLN);
  }
}
