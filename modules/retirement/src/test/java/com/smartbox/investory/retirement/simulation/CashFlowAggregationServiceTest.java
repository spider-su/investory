package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cash Flow Aggregation Service")
class CashFlowAggregationServiceTest {
  @DisplayName("aggregates Monthly Annual And One Off Income And Expenses")
  @Test
  void aggregatesMonthlyAnnualAndOneOffIncomeAndExpenses() {
    var result =
        new CashFlowAggregationService()
            .aggregate(
                new Period(date(2026, 1, 1), date(2026, 12, 31)),
                List.of(
                    flow(
                        "rent",
                        CashFlowDirection.INCOME,
                        CashFlowCadence.MONTHLY,
                        "100",
                        date(2026, 1, 1)),
                    flow(
                        "pension",
                        CashFlowDirection.INCOME,
                        CashFlowCadence.ANNUAL,
                        "50",
                        date(2026, 1, 1)),
                    flow(
                        "holiday",
                        CashFlowDirection.EXPENSE,
                        CashFlowCadence.ANNUAL,
                        "200",
                        date(2026, 1, 1)),
                    flow(
                        "purchase",
                        CashFlowDirection.EXPENSE,
                        CashFlowCadence.ONE_OFF,
                        "300",
                        date(2026, 6, 1))));
    assertThat(result.periodIncome()).isEqualByComparingTo("1250");
    assertThat(result.periodExpenses()).isEqualByComparingTo("500");
    assertThat(result.netCashFlow()).isEqualByComparingTo("750");
    assertThat(result.fundingGap()).isZero();
    assertThat(result.surplus()).isEqualByComparingTo("750");
  }

  @DisplayName("actual Replaces Only Its Projected Occurrence")
  @Test
  void actualReplacesOnlyItsProjectedOccurrence() {
    var projected =
        flow("rent", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "100", date(2026, 1, 1));
    var actual =
        new PlannedCashFlow(
            "rent",
            "rent",
            CashFlowDirection.INCOME,
            CashFlowCadence.MONTHLY,
            bd("80"),
            date(2026, 1, 1),
            date(2026, 1, 31),
            null,
            ProjectionSource.ACTUAL,
            com.smartbox.investory.shared.currency.CurrencyType.PLN);
    var result =
        new CashFlowAggregationService()
            .aggregate(
                new Period(date(2026, 1, 1), date(2026, 12, 31)), List.of(projected, actual));
    assertThat(result.actualIncome()).isEqualByComparingTo("80");
    assertThat(result.projectedIncome()).isEqualByComparingTo("1100");
  }

  private static PlannedCashFlow flow(
      String id,
      CashFlowDirection direction,
      CashFlowCadence cadence,
      String amount,
      LocalDate date) {
    return new PlannedCashFlow(
        id, id, direction, cadence, bd(amount), date, ProjectionSource.PROJECTED);
  }

  private static LocalDate date(int year, int month, int day) {
    return LocalDate.of(year, month, day);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
