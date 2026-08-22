package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAnnualProjectionServiceTest {
  @Test
  void carriesRentalForwardAtEffectiveInflationPlusSpreadGrowth() {
    var asset =
        new LongTermAssetProjectionModel(
            1L,
            "Rental",
            LongTermAssetTypeModel.REAL_ESTATE,
            CurrencyType.USD,
            BigDecimal.ZERO,
            List.of(
                new LongTermAssetProjectionModel.Period(
                    LocalDate.of(2026, 1, 1),
                    null,
                    new BigDecimal("174804"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    CashFlowTypeModel.RENT,
                    false)),
            List.of(),
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false);
    var state =
        new LongTermAnnualProjectionApi.PlanningState(
            List.of(asset), new BigDecimal("0.01"), 2026, LongTermAnnualProjectionApi.Source.PROJECTED);
    var service = new LongTermAnnualProjectionService();

    var current = service.plan(new LongTermAnnualProjectionApi.PlanningRequest(2026, BigDecimal.ZERO, state));
    var firstFuture =
        service.plan(
            new LongTermAnnualProjectionApi.PlanningRequest(
                2027, BigDecimal.ZERO, current.endState()));
    var secondFuture =
        service.plan(
            new LongTermAnnualProjectionApi.PlanningRequest(
                2028, BigDecimal.ZERO, firstFuture.endState()));

    assertThat(rental(current)).isEqualByComparingTo("174804");
    assertThat(rental(firstFuture)).isEqualByComparingTo("176552.04");
    assertThat(rental(secondFuture)).isEqualByComparingTo("178317.5604");
    assertThat(rental(firstFuture)).isPositive();
    assertThat(rental(secondFuture)).isPositive();
  }

  private static BigDecimal rental(LongTermAnnualProjectionApi.PlanningProjection projection) {
    return projection.plannedCashFlows().stream()
        .filter(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
