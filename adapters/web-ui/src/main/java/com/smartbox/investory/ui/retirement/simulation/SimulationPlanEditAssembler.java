package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.PlanningBuckets;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Assembles the model used by the simulation plan editor. */
@Component
final class SimulationPlanEditAssembler {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementPresentationClient presentation;
  private final RetirementPreviewClient preview;
  private final Clock clock;

  SimulationPlanEditAssembler(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementPresentationClient presentation,
      RetirementPreviewClient preview,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.presentation = presentation;
    this.preview = preview;
    this.clock = clock;
  }

  void assemble(
      Long portfolioId,
      Long planId,
      CurrencyType currency,
      SimulationScenario scenario,
      boolean developMode,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    int year = Year.now(clock).getValue();
    Long selectedId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    var details = selectedId == null ? null : plans.details(portfolioId, selectedId);
    SimulationAssumptions assumptions =
        details == null
            ? SimulationAssumptions.defaults(profile, 40, 95, year)
            : details.assumptions();
    model.addAttribute("profile", profile);
    model.addAttribute("displayProfile", presentation.displayProfile(profile, currency));
    model.addAttribute("assumptions", assumptions);
    model.addAttribute(
        "planningBuckets",
        PlanningBuckets.fromProfileWithBondYield(
            profile,
            assumptions.equityReturnRate(),
            PlanningBuckets.baseBondYield(
                profile, assumptions.fixedIncomeReturnRate(), assumptions.startYear())));
    model.addAttribute("planStartYear", assumptions.planStartYear());
    model.addAttribute("ageAtPlanStart", assumptions.ageAtPlanStart());
    model.addAttribute(
        "currentPlanningAge", assumptions.ageAtPlanStart() + year - assumptions.planStartYear());
    model.addAttribute("currentPlanningYear", year);
    model.addAttribute("plannedRetirementYear", assumptions.retirementYear());
    model.addAttribute("planName", details == null ? "" : details.name());
    model.addAttribute("selectedPlanId", selectedId);
    model.addAttribute("selectedScenario", scenario);
    model.addAttribute("planningDisplayCurrency", currency);
    model.addAttribute("developMode", developMode);
    var values = preview.preview(profile, assumptions, currency);
    model.addAttribute("currentRentalIncome", values.rentalIncome());
    model.addAttribute("currentBondIncome", values.bondIncome());
    model.addAttribute("plannedIncomeReferenceYear", values.plannedIncomeReferenceYear());
    model.addAttribute("plannedRentalIncome", values.plannedRentalIncome());
    model.addAttribute("plannedBondIncome", values.plannedBondIncome());
    model.addAttribute(
        "plannedEmploymentIncome",
        values.firstProjectedYear() == null
            ? null
            : values.firstProjectedYear().employmentIncome());
    model.addAttribute("plannedInvestmentProfit", values.plannedInvestmentProfit());
    model.addAttribute("plannedCapitalizedBondReturn", values.plannedCapitalizedBondReturn());
    model.addAttribute("plannedPension", values.plannedPension());
    model.addAttribute("plannedAnnualIncome", values.plannedAnnualIncome());
    if (developMode) model.addAttribute("planPreview", values);
    model.addAttribute("plans", plans.listPlans(portfolioId));
    addMoney(
        model,
        "displayMonthlyLivingCosts",
        assumptions.annualLivingExpenses().divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
        currency);
    addMoney(
        model,
        "displayTotalAnnualCosts",
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
        currency);
    addMoney(model, "displayAnnualLivingCosts", assumptions.annualLivingExpenses(), currency);
    addMoney(
        model,
        "displayMonthlyTotalCosts",
        assumptions
            .annualLivingExpenses()
            .add(assumptions.annualDiscretionaryExpenses())
            .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
        currency);
    addMoney(
        model, "displayDiscretionaryExpenses", assumptions.annualDiscretionaryExpenses(), currency);
    addMoney(model, "displayAnnualPension", assumptions.annualPension(), currency);
    addMoney(
        model, "displayAnnualEmploymentIncome", assumptions.annualEmploymentIncome(), currency);
    addMoney(
        model,
        "displayAnnualPreRetirementContribution",
        assumptions.annualPreRetirementContribution(),
        currency);
    var eventAmounts = new LinkedHashMap<Long, BigDecimal>();
    assumptions
        .futureEvents()
        .forEach(
            event ->
                eventAmounts.put(event.id(), presentation.toDisplay(event.amount(), currency)));
    model.addAttribute("displayEventAmounts", eventAmounts);
  }

  private void addMoney(Model model, String name, BigDecimal amount, CurrencyType currency) {
    model.addAttribute(name, presentation.toDisplay(amount, currency));
  }
}
