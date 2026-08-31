package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Live Year Review View")
class LiveYearReviewViewTest {
  @DisplayName("keeps Approved Plan As Benchmark And Calculates Only Forecast Variance")
  @Test
  void keepsApprovedPlanAsBenchmarkAndCalculatesOnlyForecastVariance() {
    var assumptions =
        SimulationAssumptions.defaults(
            new com.smartbox.investory.profile.api.model.InvestmentProfile(
                1L,
                CurrencyType.PLN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                java.util.List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                com.smartbox.investory.profile.api.model.ProfileAssetProjection.EMPTY,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures
                    .annualIncome(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY),
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
            new com.smartbox.investory.retirement.api.model.CurrentPlanningYear(
                2026, null, null, Map.of(), Map.of()),
            money,
            assumptions);

    assertEquals(
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
        view.planSpending());
    assertEquals(new BigDecimal("120").subtract(view.planSpending()), view.forecastVariance());
  }
}
