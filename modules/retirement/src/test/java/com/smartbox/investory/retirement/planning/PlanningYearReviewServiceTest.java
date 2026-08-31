package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Year Review Service")
class PlanningYearReviewServiceTest {
  private final PlanningYearReviewService service =
      new PlanningYearReviewService(new PlanningProgressService());

  @DisplayName("plan Vs Actual Variances Use Actual Minus Plan And Residual Reconciles")
  @Test
  void planVsActualVariancesUseActualMinusPlanAndResidualReconciles() {
    YearReview review = review("90", "25", "185", "100", "30", "100");

    assertEquals(2025, review.year());
    assertEquals(PlanProgressState.AHEAD, review.headline());
    assertEquals(new BigDecimal("-10"), impact(review, "Annual living costs"));
    assertEquals(new BigDecimal("-5"), impact(review, "Annual extras"));
    assertEquals(new BigDecimal("100"), review.otherChanges());
    assertEquals(
        review.progress().difference(),
        review.drivers().stream()
            .map(YearReview.YearReviewDriver::impact)
            .reduce(review.otherChanges(), BigDecimal::add));
  }

  @DisplayName("higher Core Spending Produces Positive Variance")
  @Test
  void higherCoreSpendingProducesPositiveVariance() {
    YearReview review = review("110", "30", "100", "100", "30", "100");

    assertEquals(new BigDecimal("10"), impact(review, "Annual living costs"));
  }

  @DisplayName("higher Discretionary Spending Uses Actual Minus Plan Sign Convention")
  @Test
  void higherDiscretionarySpendingUsesActualMinusPlanSignConvention() {
    YearReview review = review("100", "35", "100", "100", "30", "100");

    assertEquals(new BigDecimal("5"), impact(review, "Annual extras"));
  }

  @DisplayName("exposes Comparable Passive Income Variance As ADriver")
  @Test
  void exposesComparablePassiveIncomeVarianceAsADriver() {
    EnumMap<PlanningMetric, PlanningMetricValue> actual = values("100", "30", "150");
    EnumMap<PlanningMetric, PlanningMetricValue> planned = values("100", "30", "100");
    actual.put(PlanningMetric.PASSIVE_INCOME, value(PlanningMetric.PASSIVE_INCOME, "900"));
    planned.put(PlanningMetric.PASSIVE_INCOME, value(PlanningMetric.PASSIVE_INCOME, "0"));

    YearReview review = service.review(closed(actual, planned));

    assertEquals(3, review.drivers().size());
    assertEquals(new BigDecimal("-850"), review.otherChanges());
    assertEquals(new BigDecimal("900"), impact(review, "Passive income"));
  }

  @DisplayName("unavailable Overall Comparison Does Not Manufacture Other Changes")
  @Test
  void unavailableOverallComparisonDoesNotManufactureOtherChanges() {
    YearReview review = review("90", "25", null, "100", "30", "100");

    assertNull(review.otherChanges());
    assertEquals(PlanProgressState.UNAVAILABLE, review.headline());
    assertEquals(new BigDecimal("-10"), impact(review, "Annual living costs"));
    assertEquals(new BigDecimal("-5"), impact(review, "Annual extras"));
  }

  @DisplayName("exact Plan And Actual Variance Is On Plan")
  @Test
  void exactPlanAndActualVarianceIsOnPlan() {
    YearReview review = review("180", "0", "100", "180", "0", "100");

    assertEquals(PlanProgressState.ON_PLAN, review.headline());
    assertEquals(BigDecimal.ZERO, review.progress().difference());
    assertEquals(BigDecimal.ZERO, impact(review, "Annual living costs"));
  }

  private YearReview review(
      String actualCore,
      String actualExtras,
      String actualNetWorth,
      String plannedCore,
      String plannedExtras,
      String plannedNetWorth) {
    return service.review(
        closed(
            values(actualCore, actualExtras, actualNetWorth),
            values(plannedCore, plannedExtras, plannedNetWorth)));
  }

  private static PastPlanningYear closed(
      Map<PlanningMetric, PlanningMetricValue> actual,
      Map<PlanningMetric, PlanningMetricValue> planned) {
    return new PastPlanningYear(2025, PlanningYearStatus.CLOSED, null, 1L, 2L, actual, planned);
  }

  private static EnumMap<PlanningMetric, PlanningMetricValue> values(
      String core, String extras, String netWorth) {
    EnumMap<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    result.put(PlanningMetric.CORE_SPENDING, value(PlanningMetric.CORE_SPENDING, core));
    result.put(
        PlanningMetric.DISCRETIONARY_SPENDING,
        value(PlanningMetric.DISCRETIONARY_SPENDING, extras));
    if (netWorth != null)
      result.put(PlanningMetric.NET_WORTH, value(PlanningMetric.NET_WORTH, netWorth));
    return result;
  }

  private static BigDecimal impact(YearReview review, String label) {
    return review.drivers().stream()
        .filter(driver -> driver.label().equals(label))
        .findFirst()
        .orElseThrow()
        .impact();
  }

  private static PlanningMetricValue value(PlanningMetric metric, String amount) {
    return new PlanningMetricValue(
        metric, new BigDecimal(amount), null, PlanningValueSource.USER_OVERRIDE, null);
  }
}
