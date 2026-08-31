package com.smartbox.investory.retirement.api.model;

import java.util.List;

/** Display-only Plan Progress values in the selected planning currency. */
public record PlanProgressView(
    boolean available,
    String headlineDifference,
    String headlineState,
    String latestBoundary,
    List<Point> points,
    List<Point> recentPoints) {
  public PlanProgressView {
    points = List.copyOf(points == null ? List.of() : points);
    recentPoints = List.copyOf(recentPoints == null ? List.of() : recentPoints);
  }

  public static PlanProgressView unavailable() {
    return new PlanProgressView(false, null, null, null, List.of(), List.of());
  }

  public record Point(int year, String difference) {}
}
