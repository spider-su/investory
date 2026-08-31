package com.smartbox.investory.retirement.api.model;

import java.util.List;

public record PlanningTimeline(List<PlanningTimelineYear> years) {
  public PlanningTimeline {
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
