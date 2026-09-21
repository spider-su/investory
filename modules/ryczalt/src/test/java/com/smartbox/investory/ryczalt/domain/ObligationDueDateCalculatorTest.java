package com.smartbox.investory.ryczalt.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ObligationDueDateCalculatorTest {
  @Test
  void usesTypeSpecificDayOfFollowingMonth() {
    assertEquals(
        LocalDate.of(2026, 2, 20),
        ObligationDueDateCalculator.calculate(YearMonth.of(2026, 1), ObligationType.RYCZALT));
    assertEquals(
        LocalDate.of(2026, 2, 25),
        ObligationDueDateCalculator.calculate(YearMonth.of(2026, 1), ObligationType.VAT));
  }

  @Test
  void movesWeekendAndHolidayDeadlineToNextWorkingDay() {
    assertEquals(
        LocalDate.of(2026, 6, 22),
        ObligationDueDateCalculator.calculate(YearMonth.of(2026, 5), ObligationType.ZUS));
    assertEquals(
        LocalDate.of(2026, 12, 28),
        ObligationDueDateCalculator.calculate(YearMonth.of(2026, 11), ObligationType.VAT));
  }
}
