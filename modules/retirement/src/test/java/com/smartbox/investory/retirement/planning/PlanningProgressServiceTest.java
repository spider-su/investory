package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Planning Progress Service")
class PlanningProgressServiceTest {
  private final PlanningProgressService service = new PlanningProgressService();

  @DisplayName(
      "reports Ahead When Reviewed Net Worth Is One Hundred Thousand Above Frozen Baseline")
  @Test
  void reportsAheadWhenReviewedNetWorthIsOneHundredThousandAboveFrozenBaseline() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, "600000", "500000", 7L, 3L));

    assertEquals(new BigDecimal("100000"), point.difference());
    assertEquals(PlanProgressState.AHEAD, point.status());
    assertEquals("2025-12-31", point.boundaryDate().toString());
  }

  @DisplayName(
      "reports Behind When Reviewed Net Worth Is One Hundred Thousand Below Frozen Baseline")
  @Test
  void reportsBehindWhenReviewedNetWorthIsOneHundredThousandBelowFrozenBaseline() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, "400000", "500000", 7L, 3L));

    assertEquals(new BigDecimal("-100000"), point.difference());
    assertEquals(PlanProgressState.BEHIND, point.status());
  }

  @DisplayName("reports On Plan When Reviewed And Frozen Net Worth Match")
  @Test
  void reportsOnPlanWhenReviewedAndFrozenNetWorthMatch() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, "500000", "500000", 7L, 3L));

    assertEquals(BigDecimal.ZERO, point.difference());
    assertEquals(PlanProgressState.ON_PLAN, point.status());
  }

  @DisplayName("missing Reviewed Actual Net Worth Makes Progress Unavailable")
  @Test
  void missingReviewedActualNetWorthMakesProgressUnavailable() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, null, "500000", 7L, 3L));

    assertUnavailable(point);
    assertEquals(new BigDecimal("500000"), point.plannedNetWorth());
  }

  @DisplayName("missing Frozen Baseline Net Worth Makes Progress Unavailable")
  @Test
  void missingFrozenBaselineNetWorthMakesProgressUnavailable() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, "500000", null, 7L, 3L));

    assertUnavailable(point);
    assertEquals(new BigDecimal("500000"), point.actualNetWorth());
  }

  @DisplayName("draft Year Is Unavailable Even When Both Net Worth Values Exist")
  @Test
  void draftYearIsUnavailableEvenWhenBothNetWorthValuesExist() {
    assertUnavailable(
        service.compare(year(2025, PlanningYearStatus.DRAFT, "600000", "500000", 7L, 3L)));
  }

  @DisplayName("closed Snapshot Comparison Does Not Use Changed Live Values")
  @Test
  void closedSnapshotComparisonDoesNotUseChangedLiveValues() {
    PastPlanningYear closed = year(2025, PlanningYearStatus.CLOSED, "600000", "500000", 7L, 3L);
    PlanProgressPoint beforeLiveProfileChange = service.compare(closed);

    PastPlanningYear changedLiveYear =
        year(2026, PlanningYearStatus.DRAFT, "9000000", "100000", 8L, 4L);
    service.compare(changedLiveYear);

    PlanProgressPoint afterLiveProfileChange = service.compare(closed);
    assertEquals(beforeLiveProfileChange, afterLiveProfileChange);
    assertEquals(new BigDecimal("100000"), afterLiveProfileChange.difference());
  }

  @DisplayName("later Baseline Revision Does Not Change Old Closed Comparison")
  @Test
  void laterBaselineRevisionDoesNotChangeOldClosedComparison() {
    PastPlanningYear closed = year(2025, PlanningYearStatus.CLOSED, "600000", "500000", 7L, 3L);
    PastPlanningYear laterRevision =
        year(2026, PlanningYearStatus.DRAFT, "600000", "900000", 7L, 4L);

    service.compare(laterRevision);
    PlanProgressPoint point = service.compare(closed);

    assertEquals(new BigDecimal("500000"), point.plannedNetWorth());
    assertEquals(new BigDecimal("100000"), point.difference());
    assertEquals(3L, point.baselineRevisionId());
  }

  @DisplayName("exposes The Exact Frozen Baseline Provenance")
  @Test
  void exposesTheExactFrozenBaselineProvenance() {
    PlanProgressPoint point =
        service.compare(year(2025, PlanningYearStatus.CLOSED, "600000", "500000", 71L, 33L));

    assertEquals(71L, point.baselinePlanId());
    assertEquals(33L, point.baselineRevisionId());
  }

  @DisplayName(
      "global Progress Uses The Latest Chronological Point Instead Of Summing Yearly Differences")
  @Test
  void globalProgressUsesTheLatestChronologicalPointInsteadOfSummingYearlyDifferences() {
    PlanProgress progress =
        service.progress(
            List.of(
                year(2027, PlanningYearStatus.CLOSED, "600000", "300000", 7L, 4L),
                year(2025, PlanningYearStatus.CLOSED, "180000", "100000", 7L, 1L),
                year(2026, PlanningYearStatus.CLOSED, "370000", "200000", 7L, 2L)));

    assertEquals(
        List.of(2025, 2026, 2027),
        progress.points().stream().map(PlanProgressPoint::year).toList());
    assertEquals(new BigDecimal("300000"), progress.headline().difference());
    assertEquals(2027, progress.headline().year());
    assertEquals(PlanProgressState.AHEAD, progress.headline().status());
    assertEquals(4L, progress.headline().baselineRevisionId());
  }

  @DisplayName("global Progress Preserves Each Years Frozen Revision When Later Revision Exists")
  @Test
  void globalProgressPreservesEachYearsFrozenRevisionWhenLaterRevisionExists() {
    List<PastPlanningYear> historical =
        List.of(
            year(2025, PlanningYearStatus.CLOSED, "180000", "100000", 7L, 1L),
            year(2026, PlanningYearStatus.CLOSED, "370000", "200000", 7L, 2L),
            year(2027, PlanningYearStatus.CLOSED, "600000", "300000", 7L, 4L));
    PlanProgress beforeRevisionFive = service.progress(historical);

    PlanProgress afterRevisionFive =
        service.progress(
            List.of(
                historical.get(0),
                historical.get(1),
                historical.get(2),
                year(2028, PlanningYearStatus.DRAFT, "600000", "900000", 7L, 5L)));

    assertEquals(beforeRevisionFive, afterRevisionFive);
    assertEquals(
        List.of(1L, 2L, 4L),
        afterRevisionFive.points().stream().map(PlanProgressPoint::baselineRevisionId).toList());
  }

  @DisplayName("global Progress Excludes Unavailable Closed And Draft Years")
  @Test
  void globalProgressExcludesUnavailableClosedAndDraftYears() {
    PlanProgress progress =
        service.progress(
            List.of(
                year(2025, PlanningYearStatus.CLOSED, "180000", "100000", 7L, 1L),
                year(2026, PlanningYearStatus.CLOSED, null, "200000", 7L, 2L),
                year(2027, PlanningYearStatus.DRAFT, "600000", "300000", 7L, 4L)));

    assertEquals(1, progress.points().size());
    assertEquals(2025, progress.headline().year());
  }

  @DisplayName("timeline Progress Excludes Live And Projected Rows")
  @Test
  void timelineProgressExcludesLiveAndProjectedRows() {
    PastPlanningYear closed = year(2025, PlanningYearStatus.CLOSED, "180000", "100000", 7L, 1L);
    CurrentPlanningYear live = new CurrentPlanningYear(2026, 7L, 5L, null, Map.of(), Map.of());
    PlanningTimeline timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2025, 40, PlanningTimelineState.ACTUAL, closed, null, null),
                new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, live, null),
                new PlanningTimelineYear(
                    2027, 42, PlanningTimelineState.PROJECTED, null, null, null)));

    PlanProgress progress = service.progressForTimeline(timeline);

    assertEquals(List.of(2025), progress.points().stream().map(PlanProgressPoint::year).toList());
  }

  @DisplayName("headline State Can Be Behind Or On Plan")
  @Test
  void headlineStateCanBeBehindOrOnPlan() {
    PlanProgress behind =
        service.progress(
            List.of(year(2025, PlanningYearStatus.CLOSED, "400000", "500000", 7L, 1L)));
    PlanProgress onPlan =
        service.progress(
            List.of(year(2025, PlanningYearStatus.CLOSED, "500000", "500000", 7L, 1L)));

    assertEquals(PlanProgressState.BEHIND, behind.headline().status());
    assertEquals(PlanProgressState.ON_PLAN, onPlan.headline().status());
  }

  @DisplayName("no Comparable History Returns An Available Free Result")
  @Test
  void noComparableHistoryReturnsAnAvailableFreeResult() {
    PlanProgress progress =
        service.progress(
            List.of(
                year(2025, PlanningYearStatus.CLOSED, null, "100000", 7L, 1L),
                year(2026, PlanningYearStatus.DRAFT, "300000", "200000", 7L, 2L)));

    assertFalse(progress.available());
    assertEquals(List.of(), progress.points());
    assertNull(progress.headline());
  }

  @DisplayName("global And Year Review Use The Same Canonical Comparison")
  @Test
  void globalAndYearReviewUseTheSameCanonicalComparison() {
    PastPlanningYear closed = year(2025, PlanningYearStatus.CLOSED, "180000", "100000", 7L, 1L);

    assertEquals(service.compare(closed), service.progress(List.of(closed)).headline());
  }

  private static void assertUnavailable(PlanProgressPoint point) {
    assertFalse(point.available());
    assertEquals(PlanProgressState.UNAVAILABLE, point.status());
    assertNull(point.difference());
  }

  private static PastPlanningYear year(
      int year,
      PlanningYearStatus status,
      String actual,
      String planned,
      Long planId,
      Long revisionId) {
    return new PastPlanningYear(
        year, status, null, planId, revisionId, values(actual), values(planned));
  }

  private static Map<PlanningMetric, PlanningMetricValue> values(String netWorth) {
    return netWorth == null
        ? Map.of()
        : Map.of(PlanningMetric.NET_WORTH, value(PlanningMetric.NET_WORTH, netWorth));
  }

  private static PlanningMetricValue value(PlanningMetric metric, String amount) {
    return new PlanningMetricValue(
        metric, new BigDecimal(amount), null, PlanningValueSource.USER_OVERRIDE, null);
  }
}
