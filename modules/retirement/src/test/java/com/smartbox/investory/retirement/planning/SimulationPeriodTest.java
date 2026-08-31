package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Period")
class SimulationPeriodTest {
  @DisplayName("uses Inclusive Days And Leap Year Denominator")
  @Test
  void usesInclusiveDaysAndLeapYearDenominator() {
    assertEquals(
        new BigDecimal("1.000000000000"),
        SimulationPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).yearFraction());
    assertEquals(
        new BigDecimal("0.500000000000"),
        SimulationPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 7, 1)).yearFraction());
  }

  @DisplayName("current Year Boundaries Are Deterministic")
  @Test
  void currentYearBoundariesAreDeterministic() {
    assertEquals(
        new BigDecimal("1.000000000000"),
        SimulationPeriod.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).yearFraction());
    assertEquals(
        new BigDecimal("0.002739726027"),
        SimulationPeriod.of(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)).yearFraction());
  }

  @DisplayName("rejects Cross Year Periods")
  @Test
  void rejectsCrossYearPeriods() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SimulationPeriod.of(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15)));
  }

  @DisplayName("compounds Rates With Stable Scale")
  @Test
  void compoundsRatesWithStableScale() {
    SimulationPeriod halfYear =
        new SimulationPeriod(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), new BigDecimal("0.5"));
    assertEquals(new BigDecimal("0.000000000000"), halfYear.compoundRate(BigDecimal.ZERO));
    assertEquals(new BigDecimal("-0.051316701949"), halfYear.compoundRate(new BigDecimal("-0.10")));
    assertThrows(IllegalArgumentException.class, () -> halfYear.compoundRate(new BigDecimal("-1")));
  }
}
