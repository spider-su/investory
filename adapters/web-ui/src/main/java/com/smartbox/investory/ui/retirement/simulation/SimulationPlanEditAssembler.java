package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanningBuckets;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** Assembles the model used by the simulation plan editor. */
@Component
final class SimulationPlanEditAssembler {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementPreviewClient preview;
  private final RetirementPresentationApi presentation;
  private final Clock clock;

  SimulationPlanEditAssembler(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementPreviewClient preview,
      @org.springframework.beans.factory.annotation.Qualifier(
              "retirementPlanningApplicationService")
          RetirementPresentationApi presentation,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.preview = preview;
    this.presentation = presentation;
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
        details == null ? SimulationAssumptions.defaults(40, 95, year) : details.assumptions();
    model.addAttribute("profile", profile);
    var previewResponse =
        preview.preview(portfolioId, selectedId, currency, editorInput(assumptions, currency));
    model.addAttribute("displayProfile", previewResponse.displayProfile());
    model.addAttribute("assumptions", assumptions);
    model.addAttribute(
        "planningBuckets",
        PlanningBuckets.fromReviewedProfileWithBondYield(
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
    var values = previewResponse.preview();
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
    previewResponse
        .displayMoney()
        .forEach((name, amount) -> model.addAttribute("display" + capitalize(name), amount));
    model.addAttribute("displayEventAmounts", previewResponse.displayEventAmounts());
  }

  private PlanEditorInput editorInput(
      SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    var expenseProfile =
        assumptions.expenseProfile().steps().stream()
            .map(step -> new PlanEditorInput.ExpenseStageInput(step.fromYear(), step.factor()))
            .toList();
    return new PlanEditorInput(
        assumptions.ageAtPlanStart(),
        assumptions.planStartYear(),
        assumptions.endAge(),
        assumptions.retirementAge(),
        presentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
            displayCurrency),
        presentation.toDisplay(assumptions.annualDiscretionaryExpenses(), displayCurrency),
        assumptions.inflationRate(),
        assumptions.fixedIncomeReturnRate(),
        assumptions.rentalIncomeGrowthSpread(),
        assumptions.spendingGrowthSpread(),
        assumptions.equityReturnRate(),
        assumptions.safeReserveYears(),
        assumptions.equityHarvestMinimumReturnRate(),
        assumptions.equityGainHarvestRate(),
        assumptions.allowEmergencyEquityWithdrawal(),
        presentation.toDisplay(assumptions.annualEmploymentIncome(), displayCurrency),
        presentation.toDisplay(assumptions.annualPreRetirementContribution(), displayCurrency),
        presentation.toDisplay(assumptions.annualPension(), displayCurrency),
        assumptions.pensionStartAge(),
        expenseProfile.isEmpty() ? null : expenseProfile);
  }

  private static String capitalize(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
