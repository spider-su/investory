package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Simulation Chart View")
class RetirementSimulationChartViewTest {
  @DisplayName("mirrors Table Values And Uses Signed Income Minus Spending Gap")
  @Test
  void mirrorsTableValuesAndUsesSignedIncomeMinusSpendingGap() {
    var money = money("240000", "176578", "938900", "620700");
    var timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(
                    2027, 42, PlanningTimelineState.PROJECTED, null, null, null)));

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

  @DisplayName("keeps One Point Per Timeline Year And Does Not Turn Missing History Into Zero")
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

  @DisplayName("exposes Authoritative Lifecycle Marker Years")
  @Test
  void exposesAuthoritativeLifecycleMarkerYears() {
    var assumptions =
        SimulationAssumptions.defaults(null, 41, 95, 2026)
            .withRetirementAge(60)
            .withPensionStartAge(67);

    var chart =
        RetirementSimulationChartView.from(new PlanningTimeline(List.of()), Map.of(), assumptions);

    assertEquals(2045, chart.retirementYear());
    assertEquals(2052, chart.pensionStartYear());
  }

  private static PlanningTimelineMoney money(
      String spending, String income, String bondsEnd, String equitiesEnd) {
    return new PlanningTimelineMoney(
        bd(spending),
        bd(income),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        bd(bondsEnd),
        null,
        bd(equitiesEnd),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
