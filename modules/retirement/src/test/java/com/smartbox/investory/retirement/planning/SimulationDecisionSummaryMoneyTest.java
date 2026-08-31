package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Decision Summary Money")
class SimulationDecisionSummaryMoneyTest {
  @DisplayName("minimum Liquid Assets Explains Positive Zero And Unavailable Outcomes")
  @Test
  void minimumLiquidAssetsExplainsPositiveZeroAndUnavailableOutcomes() {
    var positive = summary(new BigDecimal("250000"));
    var zero = summary(BigDecimal.ZERO);
    var unavailable = summary(null);

    assertEquals("250.0K", positive.minimumLiquidAssetsDisplay());
    assertEquals("lowest projected balance", positive.minimumLiquidAssetsContext());
    assertEquals("0", zero.minimumLiquidAssetsDisplay());
    assertEquals("liquid assets depleted", zero.minimumLiquidAssetsContext());
    assertEquals("—", unavailable.minimumLiquidAssetsDisplay());
    assertEquals("not available", unavailable.minimumLiquidAssetsContext());
  }

  private static SimulationDecisionSummaryMoney summary(BigDecimal minimum) {
    return new SimulationDecisionSummaryMoney(
        SimulationScenario.BASE,
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        minimum,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        0,
        0,
        BigDecimal.ZERO,
        false);
  }
}
