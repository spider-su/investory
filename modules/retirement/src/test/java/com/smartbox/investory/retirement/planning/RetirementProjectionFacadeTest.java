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
