package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Custom Scenario Input")
class CustomScenarioInputTest {
  @DisplayName("parses Percentage Points Into Rate Deltas")
  @Test
  void parsesPercentagePointsIntoRateDeltas() {
    var input = CustomScenarioInput.parse("1.0", "-0.5", "-1.0", "2.0", "0");

    assertEquals(new BigDecimal("0.010"), input.deltas().inflation());
    assertEquals(new BigDecimal("-0.005"), input.deltas().rentalGrowth());
    assertEquals(new BigDecimal("-0.010"), input.deltas().bondReturn());
    assertEquals(new BigDecimal("0.020"), input.deltas().equityReturn());
    assertTrue(input.errors().isEmpty());
  }

  @DisplayName("invalid And Out Of Range Values Are Reported Without Throwing")
  @Test
  void invalidAndOutOfRangeValuesAreReportedWithoutThrowing() {
    var input = CustomScenarioInput.parse("abc", "21", null, "-2.5", "0");

    assertTrue(input.errors().containsKey("inflation"));
    assertTrue(input.errors().containsKey("rentalGrowth"));
    assertEquals("-2.5", input.equityReturn());
    assertEquals(0, input.deltas().inflation().compareTo(BigDecimal.ZERO));
  }
}
