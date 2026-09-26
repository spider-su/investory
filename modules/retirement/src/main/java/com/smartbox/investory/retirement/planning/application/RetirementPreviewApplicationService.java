package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementPreviewApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.retirement.preview.*;
import com.smartbox.investory.retirement.preview.PlanEditorPreviewService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.time.Clock;
import java.time.Year;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application orchestration for the plan editor preview use case. */
@Service
@Slf4j
public class RetirementPreviewApplicationService implements RetirementPreviewApi {
  private final ProfileSnapshotReader profiles;
  private final RetirementPlanApi plans;
  private final PlanEditorPreviewService previews;
  private final PlanEditorInputNormalizer normalizer;
  private final Clock clock;
  private final PlanningCurrencyPresentationService presentation;

  @Autowired
  public RetirementPreviewApplicationService(
      ProfileSnapshotReader profiles,
      RetirementPlanApi plans,
      PlanEditorPreviewService previews,
      PlanEditorInputNormalizer normalizer,
      Clock clock,
      PlanningCurrencyPresentationService presentation) {
    this.profiles = profiles;
    this.plans = plans;
    this.previews = previews;
    this.normalizer = normalizer;
    this.clock = clock;
    this.presentation = presentation;
  }

  public RetirementPreviewApplicationService(
      ProfileSnapshotReader profiles,
      RetirementPlanApi plans,
      PlanEditorPreviewService previews,
      PlanEditorInputNormalizer normalizer,
      Clock clock) {
    this(profiles, plans, previews, normalizer, clock, null);
  }

  @Override
  public PlanEditorPreview preview(
      InvestmentProfile profile, SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    return operation(
        "preview retirement plan", () -> previews.preview(profile, assumptions, displayCurrency));
  }

  @Override
  public EditorPreviewResponse editorPreview(
      Long portfolioId, Long planId, CurrencyType planningDisplayCurrency, PlanEditorInput input) {
    return operation(
        "preview retirement plan editor portfolioId=" + portfolioId,
        () -> {
          InvestmentProfile profile = profiles.loadProfile(portfolioId);
          Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
          SimulationAssumptions base =
              selectedPlanId == null
                  ? SimulationAssumptions.defaults(40, 95, Year.now(clock).getValue())
                  : plans.details(portfolioId, selectedPlanId).assumptions();
          var normalized = normalizer.normalize(input, base, planningDisplayCurrency);
          return new EditorPreviewResponse(
              true,
              normalized.warnings(),
              new EditorPreviewResponse.DerivedValues(
                  FinancialPresentation.percentage(
                      normalized.assumptions().effectiveRentalIncomeGrowthRate()),
                  FinancialPresentation.percentage(
                      normalized.assumptions().effectiveSpendingGrowthRate())),
              previews.preview(profile, normalized.assumptions(), planningDisplayCurrency),
              presentation == null
                  ? null
                  : presentation.displayProfile(profile, planningDisplayCurrency),
              presentation == null
                  ? java.util.Map.of()
                  : displayMoney(normalized.assumptions(), planningDisplayCurrency),
              presentation == null
                  ? java.util.Map.of()
                  : normalized.assumptions().futureEvents().stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              SimulationEvent::id,
                              event ->
                                  presentation.toDisplay(event.amount(), planningDisplayCurrency),
                              (left, right) -> right,
                              java.util.LinkedHashMap::new)));
        });
  }

  private java.util.Map<String, java.math.BigDecimal> displayMoney(
      SimulationAssumptions assumptions, CurrencyType currency) {
    var values = new java.util.LinkedHashMap<String, java.math.BigDecimal>();
    values.put(
        "monthlyLivingCosts",
        presentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .divide(java.math.BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
            currency));
    values.put(
        "totalAnnualCosts",
        presentation.toDisplay(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            currency));
    values.put(
        "annualLivingCosts", presentation.toDisplay(assumptions.annualLivingExpenses(), currency));
    values.put(
        "monthlyTotalCosts",
        presentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .add(assumptions.annualDiscretionaryExpenses())
                .divide(java.math.BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
            currency));
    values.put(
        "discretionaryExpenses",
        presentation.toDisplay(assumptions.annualDiscretionaryExpenses(), currency));
    values.put("annualPension", presentation.toDisplay(assumptions.annualPension(), currency));
    values.put(
        "annualEmploymentIncome",
        presentation.toDisplay(assumptions.annualEmploymentIncome(), currency));
    values.put(
        "annualPreRetirementContribution",
        presentation.toDisplay(assumptions.annualPreRetirementContribution(), currency));
    return values;
  }

  private <T> T operation(String name, java.util.function.Supplier<T> action) {
    try {
      T value = action.get();
      log.info("Retirement operation succeeded: {}", name);
      return value;
    } catch (RuntimeException exception) {
      log.error("Retirement operation failed: {}", name, exception);
      throw exception;
    }
  }
}
