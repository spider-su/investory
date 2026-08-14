package com.smartbox.investory.application.planning;

import java.util.List;

/** Ordered historical boundary comparisons with the latest comparable year as the headline. */
public record PlanProgress(List<PlanProgressPoint> points, PlanProgressPoint headline) {
  public PlanProgress {
    points = List.copyOf(points == null ? List.of() : points);
  }

  public static PlanProgress from(List<PlanProgressPoint> points) {
    List<PlanProgressPoint> ordered = List.copyOf(points == null ? List.of() : points);
    return new PlanProgress(ordered, ordered.isEmpty() ? null : ordered.getLast());
  }

  public boolean available() {
    return headline != null;
  }
}
