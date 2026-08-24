package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveYearReviewViewTest {
  @Test
  void keepsApprovedPlanAsBenchmarkAndCalculatesOnlyForecastVariance() {
    var assumptions =
        SimulationAssumptions.defaults(
            new com.smartbox.investory.retirement.profile.InvestmentProfile(
                1L,
                CurrencyType.PLN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                java.util.List.of(),
                java.util.List.of()),
            41,
            95,
            2026);
    var money =
        new PlanningTimelineMoney(
            new BigDecimal("120"),
            BigDecimal.ZERO,
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
    var view =
        new LiveYearReviewView(
            2026,
            41,
            CurrencyType.PLN,
            new com.smartbox.investory.retirement.planning.CurrentPlanningYear(
                2026, null, null, Map.of(), Map.of()),
            money,
            assumptions);

    assertEquals(
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
        view.planSpending());
    assertEquals(
        new BigDecimal("120").subtract(view.planSpending()), view.forecastVariance());
  }
}
