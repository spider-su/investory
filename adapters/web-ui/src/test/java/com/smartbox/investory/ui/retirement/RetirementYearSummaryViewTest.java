package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetirementYearSummaryViewTest {
  @Test
  void mapsCanonicalReturnsAndEndCapitalIntoSummaryBuckets() {
    var money =
        new PlanningTimelineMoney(
            bd("240000"), bd("176578"), bd("176578"), null, null, null, null, null, null, null, null, null,
            null, bd("23500"), null, bd("938900"), null, bd("620700"), null, bd("4550000"),
            null, null, null, null, bd("48000"), bd("46000"), null);
    var timeline =
        new PlanningTimeline(
            List.of(new PlanningTimelineYear(2027, 42, PlanningTimelineState.PROJECTED, null, null, null)));

    var summary = RetirementYearSummaryView.from(timeline, Map.of(2027, money)).get(2027);

    assertEquals("Projected", summary.state());
    assertEquals(bd("240000"), summary.spending());
    assertEquals(bd("-63422"), summary.netCash());
    assertEquals(bd("23500"), summary.cash().endValue());
    assertEquals(bd("48000"), summary.bonds().annualValue());
    assertEquals(bd("938900"), summary.bonds().endValue());
    assertEquals(bd("46000"), summary.equities().annualValue());
    assertEquals(bd("620700"), summary.equities().endValue());
    assertEquals(bd("176578"), summary.realEstate().annualValue());
    assertEquals(bd("4550000"), summary.realEstate().endValue());
    assertEquals("Funded", summary.status());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
