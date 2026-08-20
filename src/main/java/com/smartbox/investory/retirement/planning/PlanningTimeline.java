package com.smartbox.investory.retirement.planning;

import java.util.List;

public record PlanningTimeline(List<PlanningTimelineYear> years) {
  public PlanningTimeline {
    years = years == null ? List.of() : List.copyOf(years);
  }

  public Integer firstFailureYear() {
    return years.stream()
        .filter(row -> row.projection() != null && row.projection().failed())
        .map(PlanningTimelineYear::year)
        .min(Integer::compareTo)
        .orElse(null);
  }
}
