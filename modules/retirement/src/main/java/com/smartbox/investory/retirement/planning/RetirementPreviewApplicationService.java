package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.ProfileComposition;
import com.smartbox.investory.profile.api.ProfilePlanningReader;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.ProfileSummaryReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementPreviewApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.PlanEditorPreviewService;
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
  private final ProfileSummaryReader summaries;
  private final ProfilePlanningReader planning;
  private final ProfileSnapshotReader profileSnapshots;
  private final RetirementPlanApi plans;
  private final PlanEditorPreviewService previews;
  private final PlanEditorInputNormalizer normalizer;
  private final Clock clock;

  @Autowired
  public RetirementPreviewApplicationService(
      ProfileSummaryReader summaries,
      ProfilePlanningReader planning,
      ProfileSnapshotReader profileSnapshots,
      RetirementPlanApi plans,
      PlanEditorPreviewService previews,
      PlanEditorInputNormalizer normalizer,
      Clock clock) {
    this.summaries = summaries;
    this.planning = planning;
    this.profileSnapshots = profileSnapshots;
    this.plans = plans;
    this.previews = previews;
    this.normalizer = normalizer;
    this.clock = clock;
  }

  public RetirementPreviewApplicationService(
      ProfileSummaryReader summaries,
      ProfilePlanningReader planning,
      RetirementPlanApi plans,
      PlanEditorPreviewService previews,
      PlanEditorInputNormalizer normalizer,
      Clock clock) {
    this(summaries, planning, null, plans, previews, normalizer, clock);
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
          InvestmentProfile profile =
              profileSnapshots == null
                  ? ProfileComposition.load(summaries, planning, portfolioId)
                  : profileSnapshots.loadProfile(portfolioId);
          Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
          SimulationAssumptions base =
              selectedPlanId == null
                  ? SimulationAssumptions.defaults(profile, 40, 95, Year.now(clock).getValue())
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
              previews.preview(profile, normalized.assumptions(), planningDisplayCurrency));
        });
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
