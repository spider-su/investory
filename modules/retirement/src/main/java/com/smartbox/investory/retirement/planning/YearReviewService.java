package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Builds the small, non-attributive year review from already-approved values. */
public class YearReviewService {
  private final PlanningProgressService progress;

  public YearReviewService(PlanningProgressService progress) {
    this.progress = progress;
  }

  public YearReview review(PastPlanningYear year) {
    PlanProgressPoint point = progress.compare(year);
    List<YearReview.YearReviewDriver> drivers = new ArrayList<>();
    BigDecimal explained = BigDecimal.ZERO;
    for (PlanningMetric metric : PlanningMetric.values()) {
      if (metric == PlanningMetric.NET_WORTH) continue;
      BigDecimal variance = variance(year, metric);
      if (variance != null) {
        drivers.add(new YearReview.YearReviewDriver(metric.label(), variance));
        explained = explained.add(variance);
      }
    }
    BigDecimal other = point.difference() == null ? null : point.difference().subtract(explained);
    return new YearReview(point, List.copyOf(drivers), other);
  }

  private static BigDecimal variance(PastPlanningYear year, PlanningMetric metric) {
    BigDecimal planned = value(year.expectedValues(), metric);
    BigDecimal actual = value(year.values(), metric);
    if (planned == null || actual == null) return null;
    return actual.subtract(planned);
  }

  private static BigDecimal value(
      java.util.Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values == null ? null : values.get(metric);
    return value == null ? null : value.value();
  }
}
