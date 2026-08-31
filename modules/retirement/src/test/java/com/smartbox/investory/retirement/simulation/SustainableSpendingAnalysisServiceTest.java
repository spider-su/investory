package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sustainable Spending Analysis Service")
class SustainableSpendingAnalysisServiceTest {
  @DisplayName("finds Base And Conservative Boundaries With Bounded Search")
  @Test
  void findsBaseAndConservativeBoundariesWithBoundedSearch() {
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions assumptions = invocation.getArgument(1);
              SimulationScenario scenario = invocation.getArgument(2);
              BigDecimal limit =
                  scenario == SimulationScenario.BASE
                      ? new BigDecimal("10000")
                      : new BigDecimal("5000");
              SimulationEvaluation evaluation = mock(SimulationEvaluation.class);
              when(evaluation.sustainable())
                  .thenReturn(
                      assumptions
                              .annualLivingExpenses()
                              .add(assumptions.annualDiscretionaryExpenses())
                              .compareTo(limit)
                          <= 0);
              return evaluation;
            });
    SustainableSpendingAnalysisService service =
        new SustainableSpendingAnalysisService(evaluations);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80)
            .withRecurringSpending(new BigDecimal("4000"));

    SustainableSpendingAnalysis result =
        service.analyze(mock(InvestmentProfile.class), assumptions);

    assertEquals(0, new BigDecimal("4000").compareTo(result.currentRecurringSpending()));
    assertTrue(result.base().sustainableSpending().compareTo(new BigDecimal("10000")) <= 0);
    assertTrue(result.base().sustainableSpending().compareTo(new BigDecimal("9900")) >= 0);
    assertTrue(result.conservative().sustainableSpending().compareTo(new BigDecimal("5000")) <= 0);
    assertTrue(result.conservative().sustainableSpending().compareTo(new BigDecimal("4900")) >= 0);
    assertTrue(result.conservative().headroom().compareTo(new BigDecimal("900")) >= 0);
    assertTrue(result.conservative().headroom().compareTo(new BigDecimal("1000")) <= 0);
    assertFalse(result.conservative().currentSpendingAboveLimit());
    assertTrue(result.base().sustainableBoundaryFound());
    assertEquals(SustainableSpendingResultState.BOUNDARY_FOUND, result.base().state());
  }

  @DisplayName("reports Negative Headroom When Current Spending Is Already Unsustainable")
  @Test
  void reportsNegativeHeadroomWhenCurrentSpendingIsAlreadyUnsustainable() {
    SimulationEvaluationService evaluations = thresholdEvaluation(new BigDecimal("10000"));
    SustainableSpendingAnalysisService service =
        new SustainableSpendingAnalysisService(evaluations);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80)
            .withRecurringSpending(new BigDecimal("12000"));

    SustainableSpendingAnalysis result =
        service.analyze(mock(InvestmentProfile.class), assumptions);

    assertEquals(0, new BigDecimal("12000").compareTo(result.currentRecurringSpending()));
    assertTrue(result.base().headroom().compareTo(new BigDecimal("-2100")) >= 0);
    assertTrue(result.base().headroom().compareTo(new BigDecimal("-2000")) <= 0);
    assertTrue(result.base().currentSpendingAboveLimit());
    assertEquals(SustainableSpendingResultState.BOUNDARY_FOUND, result.base().state());
  }

  @DisplayName("zero Spending Has Normal Limit And No Percentage Headroom")
  @Test
  void zeroSpendingHasNormalLimitAndNoPercentageHeadroom() {
    SimulationEvaluationService evaluations = thresholdEvaluation(new BigDecimal("5000"));
    SustainableSpendingAnalysisService service =
        new SustainableSpendingAnalysisService(evaluations);
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80)
            .withRecurringSpending(BigDecimal.ZERO);

    SustainableSpendingAnalysis result =
        service.analyze(mock(InvestmentProfile.class), assumptions);

    assertEquals(0, BigDecimal.ZERO.compareTo(result.currentRecurringSpending()));
    assertNull(result.base().headroomPercentage());
    assertTrue(result.base().sustainableSpending().compareTo(BigDecimal.ZERO) > 0);
    assertEquals(SustainableSpendingResultState.BOUNDARY_FOUND, result.base().state());
  }

  @DisplayName("reports No Sustainable Spending When Zero Still Fails")
  @Test
  void reportsNoSustainableSpendingWhenZeroStillFails() {
    SustainableSpendingAnalysis result =
        new SustainableSpendingAnalysisService(thresholdEvaluation(new BigDecimal("-1")))
            .analyze(
                mock(InvestmentProfile.class),
                SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80)
                    .withRecurringSpending(new BigDecimal("4000")));

    assertEquals(SustainableSpendingResultState.NO_SUSTAINABLE_SPENDING, result.base().state());
    assertEquals(BigDecimal.ZERO, result.base().sustainableSpending());
  }

  @DisplayName("reports Upper Bound When Plan Is Sustainable At Maximum Search Value")
  @Test
  void reportsUpperBoundWhenPlanIsSustainableAtMaximumSearchValue() {
    SustainableSpendingAnalysis result =
        new SustainableSpendingAnalysisService(thresholdEvaluation(new BigDecimal("2000000000")))
            .analyze(
                mock(InvestmentProfile.class),
                SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80)
                    .withRecurringSpending(new BigDecimal("4000")));

    assertEquals(SustainableSpendingResultState.UPPER_BOUND_NOT_FOUND, result.base().state());
    assertFalse(result.base().sustainableBoundaryFound());
  }

  @DisplayName("spending Override Preserves The Living To Discretionary Proportion")
  @Test
  void spendingOverridePreservesTheLivingToDiscretionaryProportion() {
    SimulationAssumptions base =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    SimulationAssumptions assumptions =
        copyWithSpending(base, new BigDecimal("150"), new BigDecimal("50"));

    SimulationAssumptions changed = assumptions.withRecurringSpending(new BigDecimal("220"));

    assertEquals(0, new BigDecimal("165").compareTo(changed.annualLivingExpenses()));
    assertEquals(0, new BigDecimal("55").compareTo(changed.annualDiscretionaryExpenses()));
    assertEquals(assumptions.futureEvents(), changed.futureEvents());
    assertEquals(assumptions.fundingStrategy(), changed.fundingStrategy());
    assertEquals(assumptions.safeReserveYears(), changed.safeReserveYears());
  }

  private static SimulationEvaluationService thresholdEvaluation(BigDecimal limit) {
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions assumptions = invocation.getArgument(1);
              SimulationEvaluation evaluation = mock(SimulationEvaluation.class);
              when(evaluation.sustainable())
                  .thenReturn(
                      assumptions
                              .annualLivingExpenses()
                              .add(assumptions.annualDiscretionaryExpenses())
                              .compareTo(limit)
                          <= 0);
              return evaluation;
            });
    return evaluations;
  }

  private static SimulationAssumptions copyWithSpending(
      SimulationAssumptions a, BigDecimal living, BigDecimal discretionary) {
    return new SimulationAssumptions(
        a.currentAge(),
        a.endAge(),
        living,
        a.inflationRate(),
        a.fixedIncomeReturnRate(),
        a.equityReturnRate(),
        a.pensionStartAge(),
        a.annualPension(),
        a.capitalGainTaxRate(),
        a.startYear(),
        discretionary,
        a.futureEvents(),
        a.rentalIncomeGrowthSpread(),
        a.spendingGrowthSpread(),
        a.fundingStrategy(),
        a.safeReserveYears(),
        a.equityHarvestMinimumReturnRate(),
        a.equityGainHarvestRate(),
        a.allowEmergencyEquityWithdrawal(),
        a.retirementAge(),
        a.annualEmploymentIncome(),
        a.annualPreRetirementContribution(),
        a.fundingOrder(),
        a.expenseProfile(),
        a.projectedIncomePolicy());
  }
}
