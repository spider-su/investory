package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;
import java.util.List;

public record YearReview(
    PlanProgressPoint progress, List<YearReviewDriver> drivers, BigDecimal otherChanges) {
  public int year() {
    return progress.year();
  }

  public PlanProgressState headline() {
    return progress.status();
  }

  public record YearReviewDriver(String label, BigDecimal impact) {}
}
