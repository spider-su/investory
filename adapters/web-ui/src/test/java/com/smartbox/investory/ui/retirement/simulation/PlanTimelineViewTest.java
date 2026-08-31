package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Plan Timeline View")
class PlanTimelineViewTest {
  @DisplayName("prepares Every Year With Live Default And Lifecycle Labels")
  @Test
  void preparesEveryYearWithLiveDefaultAndLifecycleLabels() {
    var timeline =
        new PlanningTimeline(
            List.of(
                row(2025, 40, PlanningTimelineState.ACTUAL),
                row(2026, 41, PlanningTimelineState.LIVE),
                row(2027, 42, PlanningTimelineState.PROJECTED)));
    var money =
        new PlanningTimelineMoney(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(80),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var summaries =
        RetirementYearSummaryView.from(timeline, Map.of(2025, money, 2026, money, 2027, money));

    var view = PlanTimelineView.from(timeline, summaries, 2026, null);

    assertEquals(3, view.years().size());
    assertEquals(2026, view.selectedYear());
    assertEquals(40, view.years().get(0).age());
    assertEquals("Plan start", view.years().get(0).lifecycleLabel());
    assertEquals("Retirement", view.years().get(1).lifecycleLabel());
    assertEquals("Plan end", view.years().get(2).lifecycleLabel());
  }

  @DisplayName("chooses Nearest Year When No Live Row Exists And Keeps Heading Accessible")
  @Test
  void choosesNearestYearWhenNoLiveRowExistsAndKeepsHeadingAccessible() {
    var timeline =
        new PlanningTimeline(
            List.of(
                row(2025, 40, PlanningTimelineState.ACTUAL),
                row(2026, 41, PlanningTimelineState.PROJECTED),
                row(2027, 42, PlanningTimelineState.PROJECTED)));
    var money =
        new PlanningTimelineMoney(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(80),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var summaries =
        RetirementYearSummaryView.from(timeline, Map.of(2025, money, 2026, money, 2027, money));

    var view = PlanTimelineView.from(timeline, summaries, null, 2027, 2026);

    assertEquals(2026, view.selectedYear());
    assertEquals("2027 · Age 42 · Pension start", view.years().get(2).heading());
  }

  @DisplayName("prepares Liquid Capital Composition Percentages")
  @Test
  void preparesLiquidCapitalCompositionPercentages() {
    var timeline = new PlanningTimeline(List.of(row(2026, 41, PlanningTimelineState.LIVE)));
    var summary =
        new RetirementYearSummaryView(
            2026,
            41,
            "Live",
            bd("100"),
            bd("100"),
            bd("0"),
            new RetirementYearSummaryView.BucketSummary(bd("10"), null, bd("100")),
            new RetirementYearSummaryView.BucketSummary(bd("20"), null, bd("300")),
            new RetirementYearSummaryView.BucketSummary(bd("30"), null, bd("600")),
            new RetirementYearSummaryView.BucketSummary(null, null, bd("2000")),
            "Live");

    var view = PlanTimelineView.from(timeline, Map.of(2026, summary), 2026, null);
    var year = view.years().getFirst();

    assertEquals(bd("1000"), year.liquidCapitalTotal());
    assertEquals(bd("10.0"), year.cashPercent());
    assertEquals(bd("30.0"), year.bondsPercent());
    assertEquals(bd("60.0"), year.equitiesPercent());
    assertEquals(bd("100"), year.cashExpected());
    assertEquals(bd("10"), year.cashCurrent());
    assertEquals(bd("90"), year.cashChange());
    assertEquals("Now", year.capitalComparisonLabel());
  }

  @DisplayName("projected Capital Uses Start Label And Keeps Real Estate Out Of Liquid Aggregate")
  @Test
  void projectedCapitalUsesStartLabelAndKeepsRealEstateOutOfLiquidAggregate() {
    var timeline = new PlanningTimeline(List.of(row(2027, 42, PlanningTimelineState.PROJECTED)));
    var summary =
        new RetirementYearSummaryView(
            2027,
            42,
            "Projected",
            bd("100"),
            bd("0"),
            bd("0"),
            new RetirementYearSummaryView.BucketSummary(bd("10"), null, bd("20")),
            new RetirementYearSummaryView.BucketSummary(bd("20"), null, bd("30")),
            new RetirementYearSummaryView.BucketSummary(bd("30"), null, bd("50")),
            new RetirementYearSummaryView.BucketSummary(bd("100"), bd("120"), bd("2000")),
            "Projected");

    var year =
        PlanTimelineView.from(timeline, Map.of(2027, summary), 2027, null).years().getFirst();

    assertEquals(bd("100"), year.liquidCapitalTotal());
    assertEquals("Start", year.capitalComparisonLabel());
    assertEquals(bd("10"), year.cashChange());
    assertEquals(bd("10.00"), year.realEstateMonthlyIncome());
    assertEquals(bd("2000"), year.realEstateCapital());
  }

  @DisplayName("keeps Empty Liquid Capital Percentages Finite")
  @Test
  void keepsEmptyLiquidCapitalPercentagesFinite() {
    var timeline = new PlanningTimeline(List.of(row(2026, 41, PlanningTimelineState.LIVE)));
    var summary =
        new RetirementYearSummaryView(
            2026,
            41,
            "Live",
            bd("0"),
            bd("0"),
            bd("0"),
            new RetirementYearSummaryView.BucketSummary(null, null, null),
            new RetirementYearSummaryView.BucketSummary(null, null, null),
            new RetirementYearSummaryView.BucketSummary(null, null, null),
            new RetirementYearSummaryView.BucketSummary(null, null, null),
            "Live");

    var year =
        PlanTimelineView.from(timeline, Map.of(2026, summary), 2026, null).years().getFirst();

    assertEquals(BigDecimal.ZERO, year.liquidCapitalTotal());
    assertEquals(BigDecimal.ZERO, year.cashPercent());
    assertEquals(BigDecimal.ZERO, year.bondsPercent());
    assertEquals(BigDecimal.ZERO, year.equitiesPercent());
  }

  private static PlanningTimelineYear row(int year, int age, PlanningTimelineState state) {
    return new PlanningTimelineYear(year, age, state, null, null, null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
