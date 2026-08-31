package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.planning.RetirementAnalysisService;
import com.smartbox.investory.retirement.planning.RetirementProjectionFacade;
import com.smartbox.investory.retirement.planning.SimulationScenarioComparison;
import com.smartbox.investory.retirement.simulation.SimulationCustomDeltas;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.SimulationScenarioSettings;
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
      @RequestParam(required = false) String customInflationDelta,
      @RequestParam(required = false) String customRentalGrowthDelta,
      @RequestParam(required = false) String customBondReturnDelta,
      @RequestParam(required = false) String customEquityReturnDelta,
      @RequestParam(required = false) String customSpendingGrowthDelta,
      Model model) {
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    CustomScenarioInput customInput =
        CustomScenarioInput.parse(
            customInflationDelta,
            customRentalGrowthDelta,
            customBondReturnDelta,
            customEquityReturnDelta,
            customSpendingGrowthDelta);
    SimulationCustomDeltas customDeltas =
        selectedScenario == SimulationScenario.CUSTOM && customInput.errors().isEmpty()
            ? customInput.deltas()
            : SimulationCustomDeltas.zero();
    if (selectedScenario == SimulationScenario.CUSTOM && customInput.errors().isEmpty()) {
      try {
        SimulationScenarioSettings.forScenario(
            SimulationScenario.CUSTOM,
            projections.loadInput(portfolioId, selectedPlanId, 40, 95).assumptions(),
            customDeltas);
      } catch (IllegalArgumentException ex) {
        customInput =
            customInput.withError("effective", "Effective assumption is outside the valid range.");
        customDeltas = SimulationCustomDeltas.zero();
      }
    }
    var projection = projections.load(portfolioId, selectedPlanId, 40, 95, customDeltas);
    var result = analyses.analyze(projection);
    boolean customVisible = selectedScenario == SimulationScenario.CUSTOM && !customDeltas.isZero();
    SimulationScenario displayedScenario =
        customVisible ? selectedScenario : SimulationScenario.BASE;
    var displaySummaries =
        new LinkedHashMap<>(
            presentation.displaySummaries(projection.summaries(), planningDisplayCurrency));
    var page =
        new RetirementAnalysisPageView(
            portfolioId,
            selectedPlanId,
            selectedPlanId == null
                ? "Current assumptions"
                : plans.name(portfolioId, selectedPlanId),
            planningDisplayCurrency,
            displayedScenario,
            CustomScenarioView.from(customInput),
            result.available(),
            result.available()
                ? null
                : "No future planning years remain after the current-year bridge.",
            displaySummaries.get(displayedScenario),
            SimulationScenarioComparison.from(
                projection.summaries(), displaySummaries, displayedScenario, customVisible),
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
                    + " → "
                    + projection.projectedAssumptions().endAge()
                : "No future years");
    model.addAttribute("analysisPage", page);
    return "retirement-analysis";
  }
}
