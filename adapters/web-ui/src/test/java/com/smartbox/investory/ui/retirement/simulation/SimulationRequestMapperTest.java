package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Request Mapper")
class SimulationRequestMapperTest {
  private final RetirementPresentationClient presentation =
      mock(RetirementPresentationClient.class);
  private final RetirementPlanInputClient planInput = mock(RetirementPlanInputClient.class);
  private final SimulationRequestMapper mapper =
      new SimulationRequestMapper(
          presentation,
          planInput,
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  @DisplayName("legacy query keeps canonical money and converts percentage points")
  void legacyQueryKeepsCanonicalMoneyAndConvertsPercentagePoints() {
    SimulationAssumptions base = SimulationAssumptions.defaults(40, 95, 2026);

    SimulationQuery query = new SimulationQuery();
    query.setCurrentAge(41);
    query.setEndAge(96);
    query.setAnnualExpenses(new BigDecimal("999"));
    query.setAnnualExpensesCanonical(new BigDecimal("1200"));
    query.setInflation(new BigDecimal("3.5"));

    SimulationAssumptions mapped = mapper.applyLegacyOverrides(base, query.legacyOverrides());

    assertEquals(41, mapped.currentAge());
    assertEquals(96, mapped.endAge());
    assertEquals(new BigDecimal("1200"), mapped.annualLivingExpenses());
    assertEquals(new BigDecimal("0.035"), mapped.inflationRate());
  }

  @Test
  @DisplayName("edited display money is converted at the UI boundary")
  void editedDisplayMoneyIsConvertedAtUiBoundary() {
    org.mockito.Mockito.when(
            presentation.fromDisplay(new BigDecimal("100"), CurrencyType.EUR, BigDecimal.ZERO))
        .thenReturn(new BigDecimal("430"));

    BigDecimal mapped =
        mapper.resolveDisplayedMoney(
            new BigDecimal("100"), new BigDecimal("999"), true, CurrencyType.EUR, BigDecimal.ZERO);

    assertEquals(new BigDecimal("430"), mapped);
  }
}
