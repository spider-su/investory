package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseProfileTest {
  @Test
  void missingScheduleKeepsFactorAtOne() {
    assertBd("1.00", ExpenseProfile.EMPTY.factorForYear(20));
  }

  @Test
  void stepsApplyFromTheirYearUntilTheNextStep() {
    ExpenseProfile profile = new ExpenseProfile(List.of(
        new ExpenseProfileStep(8, new BigDecimal("0.90")),
        new ExpenseProfileStep(15, new BigDecimal("0.80")),
        new ExpenseProfileStep(22, new BigDecimal("0.75"))));
    assertBd("1.00", profile.factorForYear(7));
    assertBd("0.90", profile.factorForYear(8));
    assertBd("0.90", profile.factorForYear(14));
    assertBd("0.80", profile.factorForYear(15));
    assertBd("0.75", profile.factorForYear(99));
  }

  @Test
  void invalidSchedulesAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(-1, BigDecimal.ONE))));
    assertThrows(IllegalArgumentException.class,
        () -> new ExpenseProfile(List.of(
            new ExpenseProfileStep(2, BigDecimal.ONE),
            new ExpenseProfileStep(1, BigDecimal.ONE))));
    assertThrows(IllegalArgumentException.class,
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(0, BigDecimal.ONE.negate()))));
  }

  @Test
  void resolvesStagesFromPlanStartAnchor() {
    var assumptions =
        SimulationAssumptions.defaults(
                new InvestmentProfile(1L, CurrencyType.PLN, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()),
                40, 80, 2025)
            .withExpenseProfile(
                new ExpenseProfile(List.of(
                    new ExpenseProfileStep(0, BigDecimal.ONE),
                    new ExpenseProfileStep(10, BigDecimal.ONE),
                    new ExpenseProfileStep(20, new BigDecimal("0.85")),
                    new ExpenseProfileStep(30, new BigDecimal("0.75")))));
    var stages = assumptions.expenseProfile().steps();
    assertEquals(List.of(2025, 2035, 2045, 2055),
        stages.stream().map(assumptions::expenseProfileStageYear).toList());
    assertEquals(List.of(40, 50, 60, 70),
        stages.stream().map(assumptions::expenseProfileStageAge).toList());
    assertBd("1.00", assumptions.expenseProfileFactorForCalendarYear(2044));
    assertBd("0.85", assumptions.expenseProfileFactorForCalendarYear(2045));
    assertBd("0.85", assumptions.expenseProfileFactorForCalendarYear(2054));
    assertBd("0.75", assumptions.expenseProfileFactorForCalendarYear(2055));
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
