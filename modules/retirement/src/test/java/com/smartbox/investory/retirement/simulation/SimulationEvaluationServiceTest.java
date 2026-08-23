package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEvaluationServiceTest {
  @Test
  void explicitBaselineYearReachesCanonicalSimulator() {
    InvestmentProfile profile = mock(InvestmentProfile.class);
    SimulationAssumptions assumptions = mock(SimulationAssumptions.class);
    SimulationResult base =
        new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    when(simulations.simulate(profile, assumptions, SimulationScenario.BASE, 2026))
        .thenReturn(base);

    SimulationEvaluation evaluation =
        new SimulationEvaluationService(simulations)
            .evaluate(profile, assumptions, SimulationScenario.BASE, 2026);

    assertSame(base, evaluation.result());
    verify(simulations).simulate(profile, assumptions, SimulationScenario.BASE, 2026);
  }
}
