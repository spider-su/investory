package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForwardSimulationInputServiceTest {
  @Test
  void preparesOneRebasedBoundaryFromTheLiveProfile() {
    Clock clock = Clock.fixed(Instant.parse("2028-06-01T00:00:00Z"), ZoneOffset.UTC);
    ForwardSimulationContextFactory contexts = new ForwardSimulationContextFactory(clock);
    CurrentYearProjectionBridge bridge = mock(CurrentYearProjectionBridge.class);
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 40, 80, 2026)
            .withRetirementAge(60)
            .withAnnualEmploymentIncome(new BigDecimal("240000"))
            .withAnnualPreRetirementContribution(new BigDecimal("50000"));
    ForwardSimulationContext context = contexts.create(profile, assumptions);
    CurrentYearBridgeResult bridged = mock(CurrentYearBridgeResult.class);
    when(bridged.bridgedProfile()).thenReturn(profile);
    when(bridge.projectCurrentYearEnd(context)).thenReturn(bridged);

    ForwardSimulationInput input =
        new ForwardSimulationInputService(contexts, bridge).prepare(profile, assumptions);

    assertEquals(42, input.context().asOfAge());
    assertEquals(2029, input.forwardAssumptions().orElseThrow().startYear());
    assertEquals(43, input.forwardAssumptions().orElseThrow().currentAge());
    assertEquals(60, input.forwardAssumptions().orElseThrow().retirementAge());
    assertEquals(
        new BigDecimal("240000"),
        input.forwardAssumptions().orElseThrow().annualEmploymentIncome());
    assertEquals(
        new BigDecimal("50000"),
        input.forwardAssumptions().orElseThrow().annualPreRetirementContribution());
    assertEquals(bridged, input.currentYearBridge());
    verify(bridge).projectCurrentYearEnd(context);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        com.smartbox.investory.shared.currency.CurrencyType.PLN,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        List.of(),
        List.of());
  }
}
