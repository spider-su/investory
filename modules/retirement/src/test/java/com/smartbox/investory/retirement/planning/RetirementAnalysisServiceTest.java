package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.DeterministicAnalysisContext;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContext;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationResult;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysisService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RetirementAnalysisServiceTest {
  @Test
  void reusesProjectionBaseAsCanonicalEvaluationForEveryAnalysis() {
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 95, 2027);
    SimulationResult base =
        new SimulationResult(
            SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    SimulationDecisionSummary summary = SimulationDecisionSummary.from(base, assumptions);
    RetirementProjectionContext projection =
        projection(profile, assumptions, Optional.of(assumptions), base, summary);
    SustainableSpendingAnalysisService spending = mock(SustainableSpendingAnalysisService.class);
    RetirementAgeAnalysisService retirementAge = mock(RetirementAgeAnalysisService.class);
    SimulationSensitivityAnalysisService sensitivity =
        mock(SimulationSensitivityAnalysisService.class);
    when(spending.analyze(any(DeterministicAnalysisContext.class)))
        .thenReturn(mock(SustainableSpendingAnalysis.class));
    when(retirementAge.analyze(any(DeterministicAnalysisContext.class)))
        .thenReturn(mock(RetirementAgeAnalysis.class));
    when(sensitivity.analyze(any(DeterministicAnalysisContext.class)))
        .thenReturn(mock(SimulationSensitivityAnalysis.class));

    RetirementAnalysisResult result =
        new RetirementAnalysisService(spending, sensitivity, retirementAge).analyze(projection);

    ArgumentCaptor<DeterministicAnalysisContext> contextCaptor =
        ArgumentCaptor.forClass(DeterministicAnalysisContext.class);
    verify(sensitivity).analyze(contextCaptor.capture());
    DeterministicAnalysisContext context = contextCaptor.getValue();
    verify(spending).analyze(context);
    verify(retirementAge).analyze(context);
    assertTrue(result.available());
    assertSame(profile, context.profile());
    assertSame(assumptions, context.assumptions());
    assertEquals(2026, context.baselineYear());
    assertSame(base, context.canonicalBase().result());
    assertSame(summary, context.canonicalBase().decisionSummary());
  }

  @Test
  void noForwardHorizonReturnsExplicitUnavailableStateWithoutRunningAnalysis() {
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 95, 95, 2026);
    RetirementProjectionContext projection =
        projection(profile, assumptions, Optional.empty(), null, null);
    SustainableSpendingAnalysisService spending = mock(SustainableSpendingAnalysisService.class);
    RetirementAgeAnalysisService retirementAge = mock(RetirementAgeAnalysisService.class);
    SimulationSensitivityAnalysisService sensitivity =
        mock(SimulationSensitivityAnalysisService.class);

    RetirementAnalysisResult result =
        new RetirementAnalysisService(spending, sensitivity, retirementAge).analyze(projection);

    assertFalse(result.available());
    assertEquals(RetirementAnalysisState.NO_FORWARD_HORIZON, result.state());
    assertTrue(result.charts().balances().isEmpty());
    verifyNoInteractions(spending, retirementAge, sensitivity);
  }

  private static RetirementProjectionContext projection(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      Optional<SimulationAssumptions> forwardAssumptions,
      SimulationResult base,
      SimulationDecisionSummary summary) {
    ForwardSimulationContext forwardContext =
        new ForwardSimulationContext(
            profile,
            assumptions,
            2025,
            39,
            2026,
            40,
            2027,
            41,
            forwardAssumptions,
            List.of(),
            List.of(),
            0,
            true);
    ForwardSimulationInput forward =
        new ForwardSimulationInput(forwardContext, profile, forwardAssumptions);
    Map<SimulationScenario, SimulationResult> results =
        base == null ? Map.of() : Map.of(SimulationScenario.BASE, base);
    Map<SimulationScenario, SimulationDecisionSummary> summaries =
        summary == null ? Map.of() : Map.of(SimulationScenario.BASE, summary);
    return new RetirementProjectionContext(
        profile, assumptions, forward, profile, assumptions, results, summaries);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
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
  }
}
