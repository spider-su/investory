package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
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
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(0, BigDecimal.ZERO))));
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
