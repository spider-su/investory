package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetirementSimulationChartViewTest {
  @Test
  void mirrorsTableValuesAndUsesSignedIncomeMinusSpendingGap() {
    var money = money("240000", "176578", "938900", "620700");
    var timeline =
        new PlanningTimeline(
            List.of(new PlanningTimelineYear(2027, 42, PlanningTimelineState.PROJECTED, null, null, null)));

    var point =
        RetirementSimulationChartView.from(timeline, Map.of(2027, money)).points().getFirst();

    assertEquals(2027, point.year());
    assertEquals("PROJECTED", point.state());
    assertEquals(bd("240000"), point.spending());
    assertEquals(bd("176578"), point.income());
    assertEquals(bd("-63422"), point.gapOrSurplus());
    assertEquals(bd("938900"), point.bondsEnd());
    assertEquals(bd("620700"), point.equitiesEnd());
  }

  @Test
  void keepsOnePointPerTimelineYearAndDoesNotTurnMissingHistoryIntoZero() {
    var timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(2025, 40, PlanningTimelineState.ACTUAL, null, null, null),
                new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, null, null)));

    var points =
        RetirementSimulationChartView.from(timeline, Map.of(2026, money("100", "80", "900", "700")))
            .points();

    assertEquals(2, points.size());
    assertNull(points.getFirst().spending());
    assertNull(points.getFirst().income());
    assertNull(points.getFirst().gapOrSurplus());
    assertEquals(bd("100"), points.get(1).spending());
  }

  private static PlanningTimelineMoney money(
      String spending, String income, String bondsEnd, String equitiesEnd) {
    return new PlanningTimelineMoney(
        bd(spending), bd(income), null, null, null, null, null, null, null, null, null, null,
        null, null, null, bd(bondsEnd), null, bd(equitiesEnd), null, null, null, null, null, null,
        null, null, null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
