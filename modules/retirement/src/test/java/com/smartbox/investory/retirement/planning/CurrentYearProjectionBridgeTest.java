package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CurrentYearProjectionBridgeTest {
  @Test
  void januaryFirstProjectsFromJanuarySecond() {
    var bridge = bridge("2026-01-01T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo("0.997260273973");
  }

  @Test
  void midYearMatchesCanonicalPeriodStartingTomorrow() {
    var bridge = bridge("2026-07-01T00:00:00Z");
    var expected = SimulationPeriod.of(
        java.time.LocalDate.of(2026, 7, 2), java.time.LocalDate.of(2026, 12, 31))
        .yearFraction();

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(expected);
  }

  @Test
  void decemberThirtyFirstHasNoRemainingProjection() {
    var bridge = bridge("2026-12-31T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void leapYearUsesLeapYearDenominator() {
    var bridge = bridge("2024-07-01T00:00:00Z");
    var expected = SimulationPeriod.of(
        java.time.LocalDate.of(2024, 7, 2), java.time.LocalDate.of(2024, 12, 31))
        .yearFraction();

    assertThat(bridge.remainingYearFraction(2024)).isEqualByComparingTo(expected);
    assertThat(expected).isEqualByComparingTo("0.5");
  }

  private static CurrentYearProjectionBridge bridge(String instant) {
    return new CurrentYearProjectionBridge(
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), mock(RetirementSimulation.class));
  }
}
