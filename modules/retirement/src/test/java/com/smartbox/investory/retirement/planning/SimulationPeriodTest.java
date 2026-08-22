package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SimulationPeriodTest {
  @Test
  void usesInclusiveDaysAndLeapYearDenominator() {
    assertEquals(new BigDecimal("1.000000000000"),
        SimulationPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).yearFraction());
    assertEquals(new BigDecimal("0.500000000000"),
        SimulationPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 7, 1)).yearFraction());
  }

  @Test
  void currentYearBoundariesAreDeterministic() {
    assertEquals(new BigDecimal("1.000000000000"),
        SimulationPeriod.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).yearFraction());
    assertEquals(new BigDecimal("0.002739726027"),
        SimulationPeriod.of(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)).yearFraction());
  }

  @Test
  void rejectsCrossYearPeriods() {
    assertThrows(IllegalArgumentException.class,
        () -> SimulationPeriod.of(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15)));
  }

  @Test
  void compoundsRatesWithStableScale() {
    SimulationPeriod halfYear = new SimulationPeriod(
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), new BigDecimal("0.5"));
    assertEquals(new BigDecimal("0.000000000000"), halfYear.compoundRate(BigDecimal.ZERO));
    assertEquals(new BigDecimal("-0.051316701949"),
        halfYear.compoundRate(new BigDecimal("-0.10")));
    assertThrows(IllegalArgumentException.class,
        () -> halfYear.compoundRate(new BigDecimal("-1")));
  }
}
