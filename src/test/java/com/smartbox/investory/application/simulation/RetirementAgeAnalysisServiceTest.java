package com.smartbox.investory.application.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.smartbox.investory.application.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetirementAgeAnalysisServiceTest {
  @Test
  void findsEarlierAgeByExhaustiveAnnualEvaluation() {
    SimulationAssumptions assumptions = assumptions(45, 80, 60);
    SimulationEvaluationService evaluations = evaluations(57, 58);

    RetirementAgeAnalysis analysis =
        new RetirementAgeAnalysisService(evaluations).analyze(profile(), assumptions);

    assertEquals(57, analysis.base().earliestSustainableRetirementAge());
    assertEquals(3, analysis.base().headroomYears());
    assertEquals(RetirementTimingResultState.EARLIER_RETIREMENT_AVAILABLE, analysis.base().state());
    assertEquals(58, analysis.conservative().earliestSustainableRetirementAge());
    assertEquals(2, analysis.conservative().headroomYears());
    assertEquals(36, analysis.base().evaluationCount());
    assertEquals(36, analysis.conservative().evaluationCount());
  }

  @Test
  void reportsImmediateRetirementWhenCurrentAgeIsSustainable() {
    SimulationAssumptions assumptions = assumptions(50, 80, 60);
    RetirementAgeAnalysis analysis =
        new RetirementAgeAnalysisService(evaluations(50, 52)).analyze(profile(), assumptions);

    assertEquals(50, analysis.base().earliestSustainableRetirementAge());
    assertEquals(
        RetirementTimingResultState.IMMEDIATE_RETIREMENT_AVAILABLE, analysis.base().state());
  }

  @Test
  void reportsDelayWhenPlannedAgeFailsButLaterAgeWorks() {
    SimulationAssumptions assumptions = assumptions(45, 80, 55);
    RetirementAgeAnalysis analysis =
        new RetirementAgeAnalysisService(evaluations(58, 58)).analyze(profile(), assumptions);

    assertFalse(analysis.conservative().plannedRetirementSustainable());
    assertEquals(58, analysis.conservative().earliestSustainableRetirementAge());
    assertEquals(3, analysis.conservative().delayYears());
    assertEquals(RetirementTimingResultState.DELAY_REQUIRED, analysis.conservative().state());
  }

  @Test
  void reportsNoSustainableAgeInsideTheHorizon() {
    SimulationAssumptions assumptions = assumptions(45, 60, 55);
    RetirementAgeAnalysis analysis =
        new RetirementAgeAnalysisService(evaluations(61, 61)).analyze(profile(), assumptions);

    assertNull(analysis.base().earliestSustainableRetirementAge());
    assertEquals(RetirementTimingResultState.NO_SUSTAINABLE_AGE, analysis.base().state());
    assertEquals(16, analysis.base().evaluationCount());
  }

  @Test
  void reportsPlannedAgeAsTheBoundaryWhenEarlierCandidatesFail() {
    SimulationAssumptions assumptions = assumptions(45, 80, 60);
    RetirementAgeAnalysis.ScenarioResult result =
        new RetirementAgeAnalysisService(evaluations(60, 60))
            .analyze(profile(), assumptions)
            .base();

    assertEquals(60, result.earliestSustainableRetirementAge());
    assertEquals(2041, result.earliestSustainableRetirementYear());
    assertEquals(RetirementTimingResultState.PLANNED_AGE_IS_BOUNDARY, result.state());
    assertEquals(0, result.headroomYears());
  }

  @Test
  void exposesNonMonotonicResultInsteadOfCallingItARequiredDelay() {
    SimulationAssumptions assumptions = assumptions(55, 60, 57);
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions candidate = invocation.getArgument(1);
              boolean sustainable =
                  candidate.retirementAge() == 55
                      || candidate.retirementAge() == 56
                      || candidate.retirementAge() >= 58;
              return new SimulationEvaluation(
                  null,
                  null,
                  new PlanSustainabilityAssessment(
                      sustainable
                          ? PlanSustainabilityStatus.SUSTAINABLE
                          : PlanSustainabilityStatus.UNSUSTAINABLE,
                      sustainable ? null : 2050,
                      sustainable ? null : 70,
                      sustainable ? BigDecimal.ZERO : BigDecimal.ONE,
                      BigDecimal.ONE,
                      BigDecimal.ONE,
                      BigDecimal.ONE));
            });

    var result =
        new RetirementAgeAnalysisService(evaluations).analyze(profile(), assumptions).base();

    assertEquals(RetirementTimingResultState.NON_MONOTONIC_RESULT, result.state());
    assertEquals(55, result.earliestSustainableRetirementAge());
    assertEquals(6, result.evaluationCount());
    assertEquals(List.of(55, 56, 57, 58, 59, 60), result.evaluatedAges());
    assertEquals(0, result.delayYears());
  }

  @Test
  void onlyRetirementAgeChangesAcrossEvaluations() {
    SimulationAssumptions assumptions = assumptions(45, 80, 60);
    SimulationEvaluationService evaluations = evaluations(57, 58);
    new RetirementAgeAnalysisService(evaluations).analyze(profile(), assumptions);

    var captor = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(evaluations, atLeastOnce())
        .evaluate(eq(profile()), captor.capture(), eq(SimulationScenario.BASE));
    SimulationAssumptions candidate = captor.getAllValues().getFirst();
    assertEquals(assumptions.annualLivingExpenses(), candidate.annualLivingExpenses());
    assertEquals(
        assumptions.annualPreRetirementContribution(), candidate.annualPreRetirementContribution());
    assertEquals(assumptions.pensionStartAge(), candidate.pensionStartAge());
    assertNotEquals(assumptions.retirementAge(), candidate.retirementAge());
  }

  private static SimulationEvaluationService evaluations(
      int baseThreshold, int conservativeThreshold) {
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions candidate = invocation.getArgument(1);
              SimulationScenario scenario = invocation.getArgument(2);
              int threshold =
                  scenario == SimulationScenario.BASE ? baseThreshold : conservativeThreshold;
              boolean sustainable = candidate.retirementAge() >= threshold;
              return new SimulationEvaluation(
                  null,
                  null,
                  new PlanSustainabilityAssessment(
                      sustainable
                          ? PlanSustainabilityStatus.SUSTAINABLE
                          : PlanSustainabilityStatus.UNSUSTAINABLE,
                      sustainable ? null : 2050,
                      sustainable ? null : 70,
                      sustainable ? BigDecimal.ZERO : new BigDecimal("100"),
                      BigDecimal.ONE,
                      BigDecimal.ONE,
                      BigDecimal.ONE));
            });
    return evaluations;
  }

  private static SimulationAssumptions assumptions(int currentAge, int endAge, int retirementAge) {
    return SimulationAssumptions.defaults(mock(InvestmentProfile.class), currentAge, endAge)
        .withRetirementAge(retirementAge);
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
