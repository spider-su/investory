package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CashFlowSectionViewTest {
  @Test
  void includesSpendableCashOnlyAndExcludesCapitalReturns() {
    var money =
        new PlanningTimelineMoney(
            bd("240000"), bd("213683.62"), bd("174803.62"), bd("38880"), null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, bd("45979.88"), bd("45979.88"), null);
    var timeline =
        new PlanningTimeline(
            List.of(new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, null, null)));

    var flow = CashFlowSectionView.from(timeline, Map.of(2026, money), null);

    assertEquals(2, flow.income().size());
    assertEquals("Rents", flow.income().get(0).source());
    assertEquals("Bond cash income", flow.income().get(1).source());
    assertEquals(bd("213683.62"), money.totalIncome());
    assertFalse(flow.income().stream().anyMatch(item -> item.amount().equals(bd("45979.88"))));
    assertEquals("Spending", flow.destinations().get(0).target());
    assertFalse(flow.destinations().stream().anyMatch(item -> "Surplus".equals(item.target())));
    assertEquals(0, flow.funding().size());
    assertEquals(bd("213683.62"), flow.incomeUsed());
    assertEquals(bd("213683.62"), flow.totalFunded());
    assertEquals(bd("26316.38"), flow.fundingRequired());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
