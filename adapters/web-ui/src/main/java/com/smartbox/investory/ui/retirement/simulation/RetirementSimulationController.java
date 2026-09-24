package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RetirementSimulationController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final Clock clock;
  private final SimulationRequestMapper requestMapper;
  private final RetirementPresentationClient presentation;
  private final SimulationPageAssembler simulationPage;
  private final SimulationPlanEditAssembler planEditPage;

  private final SimulationCommandService commands;
  private final RetirementSandboxClient sandbox;

  private final boolean developMode;

  public RetirementSimulationController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      Clock clock,
      SimulationCommandService commands,
      RetirementSandboxClient sandbox,
      SimulationRequestMapper requestMapper,
      RetirementPresentationClient presentation,
      SimulationPageAssembler simulationPage,
      SimulationPlanEditAssembler planEditPage,
      @Value("${develop.mode:false}") boolean developMode) {
    this.profiles = profiles;
    this.plans = plans;
    this.clock = clock;
    this.requestMapper = requestMapper;
    this.presentation = presentation;
    this.simulationPage = simulationPage;
    this.planEditPage = planEditPage;
    this.commands = commands;
    this.sandbox = sandbox;
    this.developMode = developMode;
  }

  @GetMapping("/portfolios/{portfolioId}/simulation/sandbox")
  public String sandbox(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @Valid @ModelAttribute SandboxSimulationForm form,
      BindingResult binding,
      Model model) {
    form.setPortfolioId(portfolioId);
    model.addAttribute("sandboxPortfolioId", portfolioId);
    if (binding.hasErrors()) {
      model.addAttribute("sandbox", new SandboxSimulationPageView(form, null, java.util.List.of()));
      model.addAttribute("sandboxAnnualIncome", annualIncome(form));
      return "simulation-sandbox";
    }
    var result = sandbox.simulate(portfolioId, form.input());
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

  private static BigDecimal annualIncome(SandboxSimulationForm form) {
    BigDecimal rental = form.getMonthlyRentalIncome();
    BigDecimal pension = form.getMonthlyPensionIncome();
    return (rental == null ? BigDecimal.ZERO : rental)
        .add(pension == null ? BigDecimal.ZERO : pension)
        .multiply(BigDecimal.valueOf(12));
  }

  @GetMapping("/portfolios/{portfolioId}/simulation")
  public String simulation(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @ModelAttribute SimulationQuery query,
      Model model) {
    query.setPlanningDisplayCurrency(
        resolveCurrency(portfolioId, query.getPlanningDisplayCurrency()));
    model.addAttribute("simulationPage", simulationPage.assemble(portfolioId, query));
    return "simulation";
  }

  @GetMapping("/portfolios/{portfolioId}/simulation/plan/edit")
  public String editPlan(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
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

  @PostMapping("/portfolios/{portfolioId}/simulation/plans")
  public String savePlan(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @Valid @ModelAttribute SimulationPlanSaveForm form) {
    int currentYear = Year.now(clock).getValue();
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
    return "redirect:/portfolios/"
        + portfolioId
        + "/simulation"
        + (savedPlanId == null ? "?" : "?planId=" + savedPlanId + "&")
        + "planningDisplayCurrency="
        + returnCurrency
        + "&selectedScenario="
        + form.getSelectedScenario();
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/plans/{planId}/events")
  public String saveEvent(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(required = false) Long eventId,
      @RequestParam int year,
      @RequestParam String name,
      @RequestParam BigDecimal amount,
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
        presentation.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
        type,
        notes);
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/plans/{planId}/events/{eventId}/delete")
  public String deleteEvent(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
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

  @PostMapping("/portfolios/{portfolioId}/simulation/plans/{id}/delete")
  public String deletePlan(
      @PathVariable Long id,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
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
    return profiles.loadProfile(portfolioId).currency();
  }
}
