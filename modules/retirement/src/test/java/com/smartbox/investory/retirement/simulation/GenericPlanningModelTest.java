package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericPlanningModelTest {
  private final CashFlowAggregationService aggregation = new CashFlowAggregationService();

  @Test
  void aggregatesMonthlyAnnualAndOneOffFlowsWithoutCategoryRules() {
    var result = aggregation.aggregate(new Period(date(2026, 1, 1), date(2026, 12, 31)), List.of(
        flow("rent", "anything", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "100", 2026, 1, 1),
        flow("pension", "another", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "50", 2026, 1, 1),
        flow("holiday", "other", CashFlowDirection.EXPENSE, CashFlowCadence.ANNUAL, "200", 2026, 1, 1),
        flow("purchase", "other", CashFlowDirection.EXPENSE, CashFlowCadence.ONE_OFF, "300", 2026, 6, 1)));
    assertThat(result.periodIncome()).isEqualByComparingTo("1800");
    assertThat(result.periodExpenses()).isEqualByComparingTo("500");
    assertThat(result.netCashFlow()).isEqualByComparingTo("1300");
    assertThat(result.surplus()).isEqualByComparingTo("1300");
  }

  @Test
  void partialQuarterIncludesOnlyApplicableMonthsAndEvents() {
    var result = aggregation.aggregate(new Period(date(2026, 4, 1), date(2026, 6, 30)), List.of(
        flow("income", "x", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "100", 2026, 1, 1),
        flow("cost", "x", CashFlowDirection.EXPENSE, CashFlowCadence.MONTHLY, "40", 2026, 5, 1),
        flow("event", "x", CashFlowDirection.EXPENSE, CashFlowCadence.ONE_OFF, "25", 2026, 5, 5)));
    assertThat(result.periodIncome()).isEqualByComparingTo("300");
    assertThat(result.periodExpenses()).isEqualByComparingTo("105");
  }

  @Test
  void actualReplacesProjectedOccurrenceAndKeepsRemainingProjection() {
    var projected = flow("rent", "rental", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "100", 2026, 1, 1);
    var actual = new PlannedCashFlow("rent", "rental", CashFlowDirection.INCOME,
        CashFlowCadence.MONTHLY, bd("80"), date(2026, 1, 1), date(2026, 1, 31), null,
        ProjectionSource.ACTUAL, com.smartbox.investory.shared.currency.CurrencyType.PLN);
    var result = aggregation.aggregate(new Period(date(2026, 1, 1), date(2026, 12, 31)), List.of(projected, actual));
    assertThat(result.actualIncome()).isEqualByComparingTo("80");
    assertThat(result.projectedIncome()).isEqualByComparingTo("1100");
  }

  @Test
  void genericSimulationUsesReserveBeforeCapitalAndDoesNotSaveSurplus() {
    var input = new RetirementSimulationOrchestrator.GenericPlanningInput(2026, 2027, bd("60"), bd("100"),
        List.of(flow("cost", "x", CashFlowDirection.EXPENSE, CashFlowCadence.ANNUAL, "100", 2026, 1, 1)),
        (year, start, withdrawal) -> new CapitalProjection(year, start, BigDecimal.ZERO, BigDecimal.ZERO,
            start, withdrawal, withdrawal.min(start), start.subtract(withdrawal.min(start)), ProjectionSource.PROJECTED));
    var result = new RetirementSimulationOrchestrator(null, null).run(input);
    assertThat(result.years().getFirst().reserve().withdrawal()).isEqualByComparingTo("60");
    assertThat(result.years().getFirst().capital().actualWithdrawal()).isEqualByComparingTo("40");
    assertThat(result.years().getFirst().unfundedGap()).isZero();
    assertThat(result.years().getFirst().reserve().endValue()).isZero();
    assertThat(result.years().get(1).capital().startValue()).isEqualByComparingTo("60");
  }

  @Test
  void insufficientReserveAndCapitalProducesUnfundedGap() {
    var result = new RetirementSimulationOrchestrator(null, null).run(
        new RetirementSimulationOrchestrator.GenericPlanningInput(2026, 2026, bd("10"), bd("20"),
            List.of(flow("cost", "x", CashFlowDirection.EXPENSE, CashFlowCadence.ANNUAL, "100", 2026, 1, 1)),
            (year, start, withdrawal) -> new CapitalProjection(year, start, BigDecimal.ZERO, BigDecimal.ZERO,
                start, withdrawal, withdrawal.min(start), start.subtract(withdrawal.min(start)), ProjectionSource.PROJECTED)));
    assertThat(result.years().getFirst().unfundedGap()).isEqualByComparingTo("70");
  }

  @Test
  void reserveAdjustmentIsExplicitAndSurplusDoesNotCreateIt() {
    var review = new PlanningAggregationService().review(
        new Period(date(2026, 1, 1), date(2026, 12, 31)),
        new Period(date(2026, 1, 1), date(2026, 3, 31)),
        List.of(flow("income", "x", CashFlowDirection.INCOME, CashFlowCadence.MONTHLY, "100", 2026, 1, 1)),
        bd("1200"), new ReserveState(bd("50"), BigDecimal.ZERO, bd("25"), BigDecimal.ZERO, ProjectionSource.ACTUAL));
    assertThat(review.expectedFullYearNetResult()).isEqualByComparingTo("900");
    assertThat(review.reserve().reviewAdjustment()).isEqualByComparingTo("25");
    assertThat(review.reserve().endValue()).isEqualByComparingTo("75");
  }

  private static PlannedCashFlow flow(String id, String category, CashFlowDirection direction,
      CashFlowCadence cadence, String amount, int year, int month, int day) {
    return new PlannedCashFlow(id, category, direction, cadence, bd(amount), date(year, month, day), ProjectionSource.PROJECTED);
  }

  private static LocalDate date(int y, int m, int d) { return LocalDate.of(y, m, d); }
  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
