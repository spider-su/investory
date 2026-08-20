package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Compares each closed year with its own frozen year-end baseline. */
public class PlanningProgressService {
  public PlanProgressPoint compare(PastPlanningYear year) {
    BigDecimal actual = value(year.values(), PlanningMetric.NET_WORTH);
    BigDecimal planned = value(year.expectedValues(), PlanningMetric.NET_WORTH);
    if (year.status() != PlanningYearStatus.CLOSED || actual == null || planned == null)
      return new PlanProgressPoint(
          year.year(),
          LocalDate.of(year.year(), 12, 31),
          actual,
          planned,
          null,
          PlanProgressState.UNAVAILABLE,
          year.baselinePlanId(),
          year.baselineRevisionId());
    BigDecimal difference = actual.subtract(planned);
    PlanProgressState state =
        difference.signum() > 0
            ? PlanProgressState.AHEAD
            : difference.signum() < 0 ? PlanProgressState.BEHIND : PlanProgressState.ON_PLAN;
    return new PlanProgressPoint(
        year.year(),
        LocalDate.of(year.year(), 12, 31),
        actual,
        planned,
        difference,
        state,
        year.baselinePlanId(),
        year.baselineRevisionId());
  }

  /**
   * Collects independent closed-year boundary comparisons. The headline is the latest point, never
   * a sum of yearly differences.
   */
  public PlanProgress progress(List<PastPlanningYear> years) {
    List<PlanProgressPoint> points =
        (years == null ? List.<PastPlanningYear>of() : years)
            .stream()
                .filter(year -> year.status() == PlanningYearStatus.CLOSED)
                .map(this::compare)
                .filter(PlanProgressPoint::available)
                .sorted(Comparator.comparingInt(PlanProgressPoint::year))
                .toList();
    return PlanProgress.from(points);
  }

  /**
   * Reads only historical timeline records; live and projected rows cannot become progress points.
   */
  public PlanProgress progressForTimeline(PlanningTimeline timeline) {
    List<PastPlanningYear> years =
        timeline == null
            ? List.of()
            : timeline.years().stream()
                .map(PlanningTimelineYear::past)
                .filter(java.util.Objects::nonNull)
                .toList();
    return progress(years);
  }

  private static BigDecimal value(
      java.util.Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values == null ? null : values.get(metric);
    return value == null ? null : value.value();
  }
}
