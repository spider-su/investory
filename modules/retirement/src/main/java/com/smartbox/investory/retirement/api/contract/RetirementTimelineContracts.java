package com.smartbox.investory.retirement.api.contract;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.List;

/** Request bodies used by the retirement timeline HTTP resource. */
public final class RetirementTimelineContracts {
  private RetirementTimelineContracts() {}

  public record SeedHistoricalBaselineRequest(
      Long planId, InvestmentProfile profile, SimulationAssumptions assumptions) {}

  public record ForwardTimelineRequest(
      RetirementProjection projection,
      SimulationScenario scenario,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency) {}

  public record ManualValueRequest(
      BigDecimal amount,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency,
      String note) {}

  public record ForwardTimelineResponse(
      PlanningTimeline timeline, java.util.Map<Integer, PlanningTimelineMoney> money) {}

  public record HistoricalYearReviewResponse(
      PastPlanningYear planningYear,
      HistoricalReconciliationView reconciliation,
      YearReview yearReview,
      DisplayYearReview displayYearReview) {}

  public record DisplayYearReview(
      BigDecimal difference, List<YearReview.YearReviewDriver> drivers, BigDecimal otherChanges) {}

  public record DisplayYearReviewRequest(
      Long portfolioId,
      PastPlanningYear planningYear,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency) {}

  public record CurrentBaselineRequest(
      Long planId, InvestmentProfile profile, SimulationAssumptions assumptions) {}

  public record RebaselineRequest(Long planId, PlanningBaseline baseline) {}

  public record NormalizePlanEditorInputRequest(
      PlanEditorInput input,
      SimulationAssumptions base,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency) {}
}
