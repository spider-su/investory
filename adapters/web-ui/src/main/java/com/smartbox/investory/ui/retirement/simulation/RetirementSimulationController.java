package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementSandboxApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.ui.profile.ProfileClient;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RetirementSimulationController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementSandboxPlanClient sandboxPlans;
  private final Clock clock;
  private final SimulationRequestMapper requestMapper;
  private final SimulationPageAssembler simulationPage;
  private final SimulationPlanEditAssembler planEditPage;

  private final SimulationCommandService commands;
  private final RetirementSandboxApi sandbox;

  @Autowired private PortfolioContextReader portfolios;

  @Value("${develop.mode:false}")
  private boolean developMode;

  @Autowired
  public RetirementSimulationController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementSandboxPlanClient sandboxPlans,
      RetirementTimelineClient planningTimeline,
      RetirementPresentationClient presentation,
      RetirementPlanInputClient planInput,
      RetirementProjectionClient projections,
      Clock clock,
      RetirementPreviewClient planEditorPreview,
      ScenarioObservationService scenarioObservations,
      SimulationCommandService commands,
      RetirementSandboxApi sandbox) {
    this.profiles = profiles;
    this.plans = plans;
    this.sandboxPlans = sandboxPlans;
    this.clock = clock;
    this.requestMapper = new SimulationRequestMapper(presentation, planInput, clock);
    this.simulationPage =
        new SimulationPageAssembler(
            plans,
            planningTimeline,
            presentation,
            projections,
            clock,
            requestMapper,
            scenarioObservations);
    this.planEditPage =
        new SimulationPlanEditAssembler(profiles, plans, presentation, planEditorPreview, clock);
    this.commands = commands;
    this.sandbox = sandbox;
  }

  @GetMapping("/simulation/sandbox")
  public String sandbox(
      @RequestParam(required = false) Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam Map<String, String> requestParams,
      @Valid @ModelAttribute SandboxSimulationForm form,
      BindingResult binding,
      Model model) {
    form.setPortfolioId(portfolioId);
    form.setPlanId(planId);
    if (planId != null && portfolioId != null && !requestParams.containsKey("currentAge")) {
      form.apply(sandboxPlans.load(portfolioId, planId));
    }
    model.addAttribute("sandboxPortfolioId", portfolioId);
    if (binding.hasErrors()) {
      model.addAttribute("sandbox", new SandboxSimulationPageView(form, null, java.util.List.of()));
      model.addAttribute("sandboxAnnualIncome", annualIncome(form));
      return "simulation-sandbox";
    }
    var result = sandbox.simulate(form.input());
    var rows =
        result.years().stream()
            .map(
                year ->
                    new SandboxSimulationPageView.Row(
                        year.age(),
                        year.year(),
                        year.totalExpenses(),
                        year.rentalIncome(),
                        year.pensionIncome(),
                        year.cashEnd(),
                        year.fixedIncomeEnd(),
                        year.equityEnd(),
                        year.endNetWorth(),
                        year.unfundedAmount()))
            .toList();
    model.addAttribute("sandbox", new SandboxSimulationPageView(form, result, rows));
    model.addAttribute("sandboxAnnualIncome", annualIncome(form));
    return "simulation-sandbox";
  }

  @PostMapping("/simulation/sandbox/save")
  public String saveSandbox(
      @Valid @ModelAttribute SandboxSimulationForm form, BindingResult binding) {
    if (binding.hasErrors() || form.getPortfolioId() == null) {
      return "redirect:/simulation/sandbox";
    }
    Long savedId = sandboxPlans.save(form.getPortfolioId(), form.getPlanId(), form.input());
    return "redirect:/simulation/sandbox?portfolioId="
        + form.getPortfolioId()
        + "&planId="
        + savedId;
  }

  private static BigDecimal annualIncome(SandboxSimulationForm form) {
    BigDecimal rental = form.getMonthlyRentalIncome();
    BigDecimal pension = form.getMonthlyPensionIncome();
    return (rental == null ? BigDecimal.ZERO : rental)
        .add(pension == null ? BigDecimal.ZERO : pension)
        .multiply(BigDecimal.valueOf(12));
  }

  @GetMapping("/simulation")
  public String simulation(@ModelAttribute SimulationQuery query, Model model) {
    query.setPlanningDisplayCurrency(
        resolveCurrency(query.getPortfolioId(), query.getPlanningDisplayCurrency()));
    model.addAttribute("simulationPage", simulationPage.assemble(query));
    return "simulation";
  }

  @GetMapping("/simulation/plan/edit")
  public String editPlan(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    planEditPage.assemble(
        portfolioId,
        planId,
        resolveCurrency(portfolioId, planningDisplayCurrency),
        selectedScenario,
        developMode,
        model);
    return "simulation-plan-edit";
  }

  @PostMapping("/simulation/plans")
  public String savePlan(@Valid @ModelAttribute SimulationPlanSaveForm form) {
    int currentYear = Year.now(clock).getValue();
    Long portfolioId = form.getPortfolioId();
    CurrencyType planningDisplayCurrency =
        resolveCurrency(portfolioId, form.getPlanningDisplayCurrency());
    Long planId = form.getPlanId();
    var planDetails = planId == null ? null : plans.details(portfolioId, planId);
    var storedAssumptions = planDetails == null ? null : planDetails.assumptions();
    SimulationAssumptions a =
        requestMapper.mapSaveForm(storedAssumptions, form.mappingInput(), currentYear);
    // Existing-plan edits preserve its reviewed baseline. Live state becomes a frozen baseline
    // only when creating a plan or explicitly rebaselining it.
    var liveProfile = profiles.loadProfile(portfolioId);
    var planningBaseline =
        planId != null && !form.isSaveAs()
            ? planDetails.baseline()
            : liveProfile == null ? null : PlanningBaseline.fromProfile(liveProfile, currentYear);
    Long savedPlanId =
        commands.savePlan(
            portfolioId, planId, form.getName(), a, planningBaseline, form.isSaveAs());
    CurrencyType returnCurrency =
        form.getReturnPlanningDisplayCurrency() == null
            ? planningDisplayCurrency
            : form.getReturnPlanningDisplayCurrency();
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + (savedPlanId == null ? "" : "&planId=" + savedPlanId)
        + "&planningDisplayCurrency="
        + returnCurrency
        + "&selectedScenario="
        + form.getSelectedScenario();
  }

  @PostMapping("/simulation/plans/{planId}/events")
  public String saveEvent(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(required = false) Long eventId,
      @RequestParam int year,
      @RequestParam String name,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) BigDecimal canonicalAmount,
      @RequestParam(defaultValue = "false") boolean amountEdited,
      @RequestParam SimulationEventType type,
      @RequestParam(required = false) String notes,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    commands.saveEvent(
        portfolioId,
        planId,
        eventId,
        year,
        name,
        requestMapper.resolveDisplayedMoney(
            amount, canonicalAmount, amountEdited, planningDisplayCurrency, BigDecimal.ZERO),
        type,
        notes);
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{planId}/events/{eventId}/delete")
  public String deleteEvent(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @PathVariable Long eventId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    commands.deleteEvent(portfolioId, planId, eventId);
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{id}/delete")
  public String deletePlan(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long currentPlanId,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    commands.deletePlan(portfolioId, id);
    Long remainingPlanId =
        java.util.Objects.equals(id, currentPlanId)
            ? plans.resolvePlanId(portfolioId, null).orElse(null)
            : currentPlanId;
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario);
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
