package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.util.List;

/** Timeline values are denominated in {@link #currency()}, except persisted historical rows. */
public record PlanningTimeline(CurrencyType currency, List<PlanningTimelineYear> years) {
  public PlanningTimeline(List<PlanningTimelineYear> years) {
    this(FinancialPolicyDefaults.CANONICAL_CURRENCY, years);
  }

  public PlanningTimeline {
    currency = currency == null ? FinancialPolicyDefaults.CANONICAL_CURRENCY : currency;
    years = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(years);
  }

  public Integer firstFailureYear() {
    return years.stream()
        .filter(row -> row.projection() != null && row.projection().failed())
        .map(PlanningTimelineYear::year)
        .min(Integer::compareTo)
        .orElse(null);
  }
}
