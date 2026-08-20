package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YearReviewServiceTest {
  private final YearReviewService service = new YearReviewService(new PlanningProgressService());

  @Test
  void lowerCoreAndDiscretionarySpendingProducePositiveImpactsAndResidualReconciles() {
    YearReview review = review("90", "25", "185", "100", "30", "100");

    assertEquals(2025, review.year());
    assertEquals(PlanProgressState.AHEAD, review.headline());
    assertEquals(new BigDecimal("10"), impact(review, "Annual living costs"));
    assertEquals(new BigDecimal("5"), impact(review, "Annual extras"));
    assertEquals(new BigDecimal("70"), review.otherChanges());
    assertEquals(
        review.progress().difference(),
        review.drivers().stream()
            .map(YearReview.YearReviewDriver::impact)
            .reduce(review.otherChanges(), BigDecimal::add));
  }

  @Test
  void higherCoreSpendingProducesNegativeImpact() {
    YearReview review = review("110", "30", "100", "100", "30", "100");

    assertEquals(new BigDecimal("-10"), impact(review, "Annual living costs"));
  }

  @Test
  void higherDiscretionarySpendingUsesTheSameNegativeSignConvention() {
    YearReview review = review("100", "35", "100", "100", "30", "100");

    assertEquals(new BigDecimal("-5"), impact(review, "Annual extras"));
  }

  @Test
  void doesNotDisplayLegacyPassiveIncomeAsAHistoricalDriver() {
    EnumMap<PlanningMetric, PlanningMetricValue> actual = values("100", "30", "150");
    EnumMap<PlanningMetric, PlanningMetricValue> planned = values("100", "30", "100");
    actual.put(PlanningMetric.PASSIVE_INCOME, value(PlanningMetric.PASSIVE_INCOME, "900"));
    planned.put(PlanningMetric.PASSIVE_INCOME, value(PlanningMetric.PASSIVE_INCOME, "0"));

    YearReview review = service.review(closed(actual, planned));

    assertEquals(2, review.drivers().size());
    assertEquals(new BigDecimal("50"), review.otherChanges());
    assertTrue(review.drivers().stream().noneMatch(driver -> driver.label().contains("income")));
  }

  @Test
  void unavailableOverallComparisonDoesNotManufactureOtherChanges() {
    YearReview review = review("90", "25", null, "100", "30", "100");

    assertNull(review.otherChanges());
    assertEquals(PlanProgressState.UNAVAILABLE, review.headline());
    assertEquals(new BigDecimal("10"), impact(review, "Annual living costs"));
    assertEquals(new BigDecimal("5"), impact(review, "Annual extras"));
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
