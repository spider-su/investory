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
    BigDecimal spending = spendingImpact(year, PlanningMetric.CORE_SPENDING);
    if (spending != null) {
      drivers.add(new YearReview.YearReviewDriver("Annual living costs", spending));
      explained = explained.add(spending);
    }
    BigDecimal extras = spendingImpact(year, PlanningMetric.DISCRETIONARY_SPENDING);
    if (extras != null) {
      drivers.add(new YearReview.YearReviewDriver("Annual extras", extras));
      explained = explained.add(extras);
    }
    BigDecimal other = point.difference() == null ? null : point.difference().subtract(explained);
    return new YearReview(point, List.copyOf(drivers), other);
  }

  private static BigDecimal spendingImpact(PastPlanningYear year, PlanningMetric metric) {
    BigDecimal planned = value(year.expectedValues(), metric);
    BigDecimal actual = value(year.values(), metric);
    if (planned == null || actual == null) return null;
    return planned.subtract(actual);
  }

  private static BigDecimal value(
      java.util.Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values == null ? null : values.get(metric);
    return value == null ? null : value.value();
  }
}
