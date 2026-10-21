package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.application.*;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Profile Application Service")
class RetirementProfileApplicationServiceTest {

  @DisplayName("reports Unavailable When Portfolio Has No Saved Plan")
  @Test
  void reportsUnavailableWhenPortfolioHasNoSavedPlan() {
    RetirementPlanApi plans = mock(RetirementPlanApi.class);
    PlanningCurrencyPresentationService presentation =
        mock(PlanningCurrencyPresentationService.class);
    when(plans.resolvePlanId(3L, null)).thenReturn(Optional.empty());
    var service =
        new RetirementProfileApplicationService(
            plans,
            presentation,
            Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));

    var result = service.currentYearAnnualCost(3L, CurrencyType.USD);

    assertThat(result.available()).isFalse();
    assertThat(result.amount()).isNull();
    assertThat(result.year()).isEqualTo(2026);
    verifyNoInteractions(presentation);
  }

  @DisplayName("reports Full Current-Year Plan Spending")
  @Test
  void reportsFullCurrentYearPlanSpending() {
    RetirementPlanApi plans = mock(RetirementPlanApi.class);
    PlanningMoneyConversionService money = mock(PlanningMoneyConversionService.class);
    PlanningCurrencyPresentationService presentation =
        new PlanningCurrencyPresentationService(money);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(40, 80, 2025).toBuilder()
            .annualLivingExpenses(new BigDecimal("192000"))
            .annualDiscretionaryExpenses(new BigDecimal("60000"))
            .inflationRate(new BigDecimal("0.03"))
            .spendingGrowthSpread(new BigDecimal("-0.015"))
            .build();
    when(plans.resolvePlanId(3L, null)).thenReturn(Optional.of(9L));
    when(plans.details(3L, 9L)).thenReturn(new PlanDetails(9L, "Plan", assumptions, null));
    when(money.toDisplay(new BigDecimal("252000"), CurrencyType.USD))
        .thenReturn(new BigDecimal("63000"));

    var service =
        new RetirementProfileApplicationService(
            plans,
            presentation,
            Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));

    var result = service.currentYearAnnualCost(3L, CurrencyType.USD);

    assertThat(result.amount()).isEqualByComparingTo("63000");
    assertThat(result.year()).isEqualTo(2026);
  }
}
