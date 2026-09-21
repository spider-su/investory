package com.smartbox.investory.ryczalt.integration.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FxRateDatePolicyTest {
  @Test
  void usesPriorBusinessDayForWeekdays() {
    assertEquals(
        LocalDate.of(2026, 9, 18), FxRateDatePolicy.priorBusinessDay(LocalDate.of(2026, 9, 21)));
  }

  @Test
  void usesPreviousDayWhenAccountingDateIsTuesday() {
    assertEquals(
        LocalDate.of(2026, 9, 21), FxRateDatePolicy.priorBusinessDay(LocalDate.of(2026, 9, 22)));
  }
}
