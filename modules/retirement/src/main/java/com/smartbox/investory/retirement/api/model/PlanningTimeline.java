package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import java.util.Objects;

/** Timeline values, including persisted historical rows, are denominated in {@link #currency()}. */
public record PlanningTimeline(CurrencyType currency, List<PlanningTimelineYear> years) {
  public PlanningTimeline {
    currency = Objects.requireNonNull(currency, "Portfolio local currency is required");
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
