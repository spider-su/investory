package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContext;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetirementProjectionFacadeTest {
  @Test
  void futureProjectionUsesFrozenBaselineInsteadOfLiveBalances() {
    InvestmentProfile live = new InvestmentProfile(1L, CurrencyType.PLN,
        new BigDecimal("675000"), new BigDecimal("300000"), new BigDecimal("975000"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
        BigDecimal.ZERO, List.of(), List.of(), new BigDecimal("10"), BigDecimal.ZERO);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(live, 40, 45, 2025);
    ForwardSimulationContext context = new ForwardSimulationContextFactory(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)).create(live, assumptions);
    ForwardSimulationInputService forwardInputs = mock(ForwardSimulationInputService.class);
    when(forwardInputs.prepare(any(), eq(assumptions)))
        .thenReturn(new ForwardSimulationInput(context, live, Optional.empty()));
    RetirementProjectionFacade facade = new RetirementProjectionFacade(mock(InvestmentProfileFacade.class),
        mock(SimulationPlanService.class), forwardInputs, mock(RetirementSimulation.class),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    facade.project(live, assumptions, new PlanningBaseline(2026, new BigDecimal("90000"),
        new BigDecimal("500000"), new BigDecimal("280000"), new BigDecimal("12"), BigDecimal.ZERO));

    var captor = org.mockito.ArgumentCaptor.forClass(InvestmentProfile.class);
    verify(forwardInputs).prepare(captor.capture(), eq(assumptions));
    assertEquals(new BigDecimal("590000"), captor.getValue().marketPortfolioValue());
    assertEquals(new BigDecimal("90000"), captor.getValue().liquidAssets());

    InvestmentProfile changedLive = new InvestmentProfile(1L, CurrencyType.PLN,
        new BigDecimal("1200000"), new BigDecimal("990000"), new BigDecimal("2190000"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("250000"),
        BigDecimal.ZERO, List.of(), List.of(), new BigDecimal("999"), BigDecimal.ZERO);
    facade.project(changedLive, assumptions, new PlanningBaseline(2026, new BigDecimal("90000"),
        new BigDecimal("500000"), new BigDecimal("280000"), new BigDecimal("12"), BigDecimal.ZERO));
    verify(forwardInputs, times(2)).prepare(captor.capture(), eq(assumptions));
    assertEquals(new BigDecimal("590000"), captor.getAllValues().get(1).marketPortfolioValue());
    assertEquals(new BigDecimal("90000"), captor.getAllValues().get(1).liquidAssets());
  }

  @Test
  void preservesNoForwardProjectionStateWithoutCallingSimulationEngine() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 95, 2026);
    ForwardSimulationContext context =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .create(profile, assumptions);
    ForwardSimulationInputService forwardInputs = mock(ForwardSimulationInputService.class);
    when(forwardInputs.prepare(profile, assumptions))
        .thenReturn(new ForwardSimulationInput(context, profile, Optional.empty()));
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    RetirementProjectionFacade facade =
        new RetirementProjectionFacade(
            mock(InvestmentProfileFacade.class),
            mock(SimulationPlanService.class),
            forwardInputs,
            simulations,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    RetirementProjectionContext result = facade.project(profile, assumptions);

    assertEquals(profile, result.profile());
    assertEquals(assumptions, result.projectedAssumptions());
    assertTrue(result.scenarioResults().isEmpty());
    assertTrue(result.summaries().isEmpty());
    verifyNoInteractions(simulations);
  }
}
