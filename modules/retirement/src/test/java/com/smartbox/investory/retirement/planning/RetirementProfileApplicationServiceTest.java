package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
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
}
