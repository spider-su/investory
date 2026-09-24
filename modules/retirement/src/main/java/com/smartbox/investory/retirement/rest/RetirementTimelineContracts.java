package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;

/** Request bodies used by the retirement timeline HTTP resource. */
public final class RetirementTimelineContracts {
  private RetirementTimelineContracts() {}

  public record SeedHistoricalBaselineRequest(
      Long planId, InvestmentProfile profile, SimulationAssumptions assumptions) {}

  public record ForwardTimelineRequest(
      RetirementProjection projection, SimulationScenario scenario) {}

  public record ManualValueRequest(BigDecimal amount, String note) {}

  public record CurrentBaselineRequest(
      Long planId, InvestmentProfile profile, SimulationAssumptions assumptions) {}

  public record RebaselineRequest(Long planId, PlanningBaseline baseline) {}

  public record NormalizePlanEditorInputRequest(
      PlanEditorInput input,
      SimulationAssumptions base,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency) {}
}
