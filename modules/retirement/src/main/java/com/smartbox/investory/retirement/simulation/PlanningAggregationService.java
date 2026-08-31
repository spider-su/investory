package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Shared annual, quarterly, and partial-period planning calculations. */
public final class PlanningAggregationService {
  private final CashFlowAggregationService flows = new CashFlowAggregationService();

  public CashFlowAggregationService.Result aggregate(Period period, List<PlannedCashFlow> planned) {
    return flows.aggregate(period, planned);
  }

  public CashFlowAggregationService.Result projected(Period period, List<PlannedCashFlow> planned) {
    return flows.aggregateProjected(period, planned);
  }

  public ReviewResult review(
      Period fullYear,
      Period throughReviewDate,
      List<PlannedCashFlow> flows,
      BigDecimal originalPlanNetResult,
      ReserveState reserve) {
    CashFlowAggregationService.Result actual = this.flows.aggregate(
        throughReviewDate, flows, true, false);
    Period remaining = new Period(throughReviewDate.end().plusDays(1), fullYear.end());
    CashFlowAggregationService.Result projected = remaining.start().isAfter(remaining.end())
        ? new CashFlowAggregationService.Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        : this.flows.aggregateProjectedRaw(remaining, flows);
    BigDecimal expected = actual.netCashFlow().add(projected.netCashFlow());
    BigDecimal variance = expected.subtract(originalPlanNetResult == null ? BigDecimal.ZERO : originalPlanNetResult);
    return new ReviewResult(actual.periodIncome(), actual.periodExpenses(), projected.periodIncome(),
        projected.periodExpenses(), expected, variance, reserve);
  }
}
