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

class PlanTimelineViewTest {
  @Test
  void preparesEveryYearWithLiveDefaultAndLifecycleLabels() {
    var timeline = new PlanningTimeline(List.of(
        row(2025, 40, PlanningTimelineState.ACTUAL),
        row(2026, 41, PlanningTimelineState.LIVE),
        row(2027, 42, PlanningTimelineState.PROJECTED)));
    var money = new PlanningTimelineMoney(BigDecimal.valueOf(100), BigDecimal.valueOf(80), null, null,
        null, null, null, null, null, null, null, null);
    var summaries = RetirementYearSummaryView.from(timeline, Map.of(2025, money, 2026, money, 2027, money));

    var view = PlanTimelineView.from(timeline, summaries, 2026, null);

    assertEquals(3, view.years().size());
    assertEquals(2026, view.selectedYear());
    assertEquals(40, view.years().get(0).age());
    assertEquals("Plan start", view.years().get(0).lifecycleLabel());
    assertEquals("Retirement", view.years().get(1).lifecycleLabel());
    assertEquals("Plan end", view.years().get(2).lifecycleLabel());
  }

  private static PlanningTimelineYear row(int year, int age, PlanningTimelineState state) {
    return new PlanningTimelineYear(year, age, state, null, null, null);
  }
}
