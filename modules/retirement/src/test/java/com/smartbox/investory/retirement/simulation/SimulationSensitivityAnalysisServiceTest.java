package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.*;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Sensitivity Analysis Service")
class SimulationSensitivityAnalysisServiceTest {
  @DisplayName("context Keeps Projection Base As Sensitivity Baseline")
  @Test
  void contextKeepsProjectionBaseAsSensitivityBaseline() {
    InvestmentProfile profile = profileWithMarketBuckets();
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 80, 2027);
    SimulationResult baseResult =
        new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    SimulationDecisionSummary baseSummary = SimulationDecisionSummary.from(baseResult, assumptions);
    SimulationEvaluation canonicalBase =
        new SimulationEvaluation(
            baseResult, baseSummary, PlanSustainabilityAssessment.from(baseSummary));
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    FrozenBondCashFlowProjection bondProjection = mock(FrozenBondCashFlowProjection.class);
    when(evaluations.evaluate(any(), any(), eq(SimulationScenario.BASE), eq(2026)))
        .thenReturn(canonicalBase);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(evaluations, bondProjection)
            .analyze(new DeterministicAnalysisContext(profile, assumptions, 2026, canonicalBase));

    assertSame(canonicalBase, result.baseline());
    assertFalse(
        result.drivers().stream()
            .anyMatch(driver -> driver.driver() == SensitivityDriver.FIXED_INCOME_RETURN));
    verify(evaluations, never()).evaluate(profile, assumptions, SimulationScenario.BASE);
    verify(evaluations, atLeastOnce())
        .evaluate(eq(profile), any(), eq(SimulationScenario.BASE), eq(2026));
    verify(bondProjection).hasPlanBondReturnExposure(profile, 2026, 2026);
  }

  @DisplayName("invalid Perturbation Is Unavailable Without Crashing Analysis")
  @Test
  void invalidPerturbationIsUnavailableWithoutCrashingAnalysis() {
    InvestmentProfile profile = profileWithMarketBuckets();
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 40, 80, 2027).toBuilder()
            .inflationRate(new BigDecimal("-0.995"))
            .build();

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    SimulationSensitivityResult inflation =
        result.drivers().stream()
            .filter(driver -> driver.driver() == SensitivityDriver.INFLATION)
            .findFirst()
            .orElseThrow();
    assertNull(inflation.lowerEvaluation());
    assertNull(inflation.lowerTestedValue());
    assertNotNull(inflation.higherEvaluation());
  }

  @DisplayName("simulator Illegal Argument Exception Is Not Hidden As Unavailable")
  @Test
  void simulatorIllegalArgumentExceptionIsNotHiddenAsUnavailable() {
    InvestmentProfile profile = profileWithMarketBuckets();
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 80, 2027);
    SimulationResult baseResult =
        new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of());
    SimulationDecisionSummary baseSummary = SimulationDecisionSummary.from(baseResult, assumptions);
    SimulationEvaluation canonicalBase =
        new SimulationEvaluation(
            baseResult, baseSummary, PlanSustainabilityAssessment.from(baseSummary));
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), eq(SimulationScenario.BASE), eq(2026)))
        .thenThrow(new IllegalArgumentException("simulator defect"));

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SimulationSensitivityAnalysisService(evaluations)
                    .analyze(
                        new DeterministicAnalysisContext(
                            profile, assumptions, 2026, canonicalBase)));

    assertEquals("simulator defect", error.getMessage());
  }

  @DisplayName("failure Risk Ranks Ahead Of Wealth Only Impact")
  @Test
  void failureRiskRanksAheadOfWealthOnlyImpact() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile = profileWithMarketBuckets();
    SimulationEvaluationService evaluations = mockEvaluations(assumptions);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(evaluations).analyze(profile, assumptions);

    var recurring =
        result.drivers().stream()
            .filter(driver -> driver.driver() == SensitivityDriver.RECURRING_SPENDING)
            .findFirst()
            .orElseThrow();
    assertEquals(SensitivityDriverCategory.PLANNING_LEVER, recurring.driver().category());
    assertEquals(
        SensitivityImpact.CRITICAL,
        SimulationSensitivityAnalysisService.classify(
            evaluation(true, "0", "5", "100000", "100000"),
            evaluation(false, "1000", "5", "90000", "90000"),
            BigDecimal.ZERO,
            new BigDecimal("-10000"),
            new BigDecimal("-10000")));
    verify(evaluations, atLeast(1)).evaluate(eq(profile), any(), eq(SimulationScenario.BASE));
  }

  @DisplayName("inactive Drivers Are Not Presented")
  @Test
  void inactiveDriversAreNotPresented() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
            BigDecimal.ZERO
                .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    assertEquals(
        java.util.Set.of(
            SensitivityDriver.SPENDING_GROWTH,
            SensitivityDriver.INFLATION,
            SensitivityDriver.RECURRING_SPENDING),
        result.drivers().stream()
            .map(SimulationSensitivityResult::driver)
            .collect(java.util.stream.Collectors.toSet()));
  }

  @DisplayName("growth Cells Expose Effective Rate Not Stored Spread")
  @Test
  void growthCellsExposeEffectiveRateNotStoredSpread() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2027);
    SimulationEvaluationService evaluations = mockEvaluations(assumptions);
    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(evaluations)
            .analyze(profileWithRentalIncome(), assumptions);

    var rental =
        result.drivers().stream()
            .filter(driver -> driver.driver() == SensitivityDriver.RENTAL_INCOME_GROWTH)
            .findFirst()
            .orElseThrow();
    assertEquals(0, rental.lowerTestedValue().compareTo(new BigDecimal("0.04")));
    assertEquals(0, rental.baseTestedValue().compareTo(new BigDecimal("0.045")));
    assertEquals(0, rental.higherTestedValue().compareTo(new BigDecimal("0.05")));
  }

  @DisplayName("positive Reserve Movement Is Not Reserve Deterioration")
  @Test
  void positiveReserveMovementIsNotReserveDeterioration() {
    SimulationEvaluation base = evaluation(true, "0", "5", "100000", "100000");
    SimulationEvaluation improved = evaluation(true, "0", "6", "100000", "100000");
    assertEquals(
        SensitivityImpact.NEGLIGIBLE,
        SimulationSensitivityAnalysisService.classify(
            base, improved, new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO));
  }

  @DisplayName("harmful Comparison Honors Failure Transition And Magnitude")
  @Test
  void harmfulComparisonHonorsFailureTransitionAndMagnitude() {
    SimulationEvaluation base = evaluation(true, "0", "5", "100000", "100000");
    SimulationEvaluation failsSlightly = evaluation(false, "100", "5", "90000", "90000");
    SimulationEvaluation failsMore = evaluation(false, "1000", "4", "80000", "80000");

    assertTrue(SimulationSensitivityAnalysisService.compareHarm(failsSlightly, base, base) > 0);
    assertTrue(
        SimulationSensitivityAnalysisService.compareHarm(failsMore, failsSlightly, base) > 0);
    assertEquals(
        0, SimulationSensitivityAnalysisService.compareHarm(failsSlightly, failsSlightly, base));
  }

  @DisplayName("impact Thresholds Use Directional Boundaries")
  @Test
  void impactThresholdsUseDirectionalBoundaries() {
    SimulationEvaluation base = evaluation(true, "0", "5", "100000", "100000");
    assertEquals(
        SensitivityImpact.NEGLIGIBLE,
        SimulationSensitivityAnalysisService.classify(
            base, base, new BigDecimal("-0.499"), BigDecimal.ZERO, BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.MODERATE,
        SimulationSensitivityAnalysisService.classify(
            base, base, new BigDecimal("-0.5"), BigDecimal.ZERO, BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.MODERATE,
        SimulationSensitivityAnalysisService.classify(
            base, base, new BigDecimal("-0.501"), BigDecimal.ZERO, BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.NEGLIGIBLE,
        SimulationSensitivityAnalysisService.classify(
            base, base, BigDecimal.ZERO, new BigDecimal("-999"), BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.MODERATE,
        SimulationSensitivityAnalysisService.classify(
            base, base, BigDecimal.ZERO, new BigDecimal("-1000"), BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.MODERATE,
        SimulationSensitivityAnalysisService.classify(
            base, base, BigDecimal.ZERO, new BigDecimal("-1001"), BigDecimal.ZERO));
    assertEquals(
        SensitivityImpact.NEGLIGIBLE,
        SimulationSensitivityAnalysisService.classify(
            base, base, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-999")));
    assertEquals(
        SensitivityImpact.WEALTH_ONLY,
        SimulationSensitivityAnalysisService.classify(
            base, base, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1000")));
  }

  @DisplayName("introducing ARecurring Funding Gap Is AReserve Deterioration")
  @Test
  void introducingARecurringFundingGapIsAReserveDeterioration() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
            BigDecimal.ZERO
                .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    BigDecimal baselineSpending =
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), eq(SimulationScenario.BASE)))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions candidate = invocation.getArgument(1);
              boolean gap =
                  candidate
                          .annualLivingExpenses()
                          .add(candidate.annualDiscretionaryExpenses())
                          .compareTo(baselineSpending)
                      > 0;
              return new SimulationEvaluation(
                  null,
                  null,
                  new PlanSustainabilityAssessment(
                      PlanSustainabilityStatus.SUSTAINABLE,
                      null,
                      null,
                      BigDecimal.ZERO,
                      gap ? BigDecimal.ONE : BigDecimal.ZERO,
                      BigDecimal.ONE,
                      BigDecimal.ONE,
                      gap));
            });

    var result =
        new SimulationSensitivityAnalysisService(evaluations).analyze(profile, assumptions);
    var spending =
        result.drivers().stream()
            .filter(driver -> driver.driver() == SensitivityDriver.RECURRING_SPENDING)
            .findFirst()
            .orElseThrow();

    assertFalse(spending.baseline().sustainability().recurringFundingGapRequired());
    assertTrue(spending.adverse().sustainability().recurringFundingGapRequired());
    assertTrue(spending.adverseIntroducesRecurringFundingGap());
    assertEquals(BigDecimal.ZERO, spending.reserveCoverageDelta());
    assertEquals(SensitivityImpact.HIGH, spending.impact());
  }

  private static SimulationEvaluationService mockEvaluations(
      SimulationAssumptions baselineAssumptions) {
    BigDecimal baselineSpending =
        baselineAssumptions
            .annualLivingExpenses()
            .add(baselineAssumptions.annualDiscretionaryExpenses());
    SimulationEvaluationService evaluations = mock(SimulationEvaluationService.class);
    when(evaluations.evaluate(any(), any(), eq(SimulationScenario.BASE)))
        .thenAnswer(
            invocation -> {
              SimulationAssumptions assumptions = invocation.getArgument(1);
              boolean spendingAdverse =
                  assumptions
                          .annualLivingExpenses()
                          .add(assumptions.annualDiscretionaryExpenses())
                          .compareTo(baselineSpending)
                      > 0;
              boolean growthAdverse =
                  assumptions
                          .spendingGrowthSpread()
                          .compareTo(baselineAssumptions.spendingGrowthSpread())
                      > 0;
              boolean equityAdverse =
                  assumptions.equityReturnRate().compareTo(baselineAssumptions.equityReturnRate())
                      < 0;
              boolean fixedIncomeAdverse =
                  assumptions
                          .fixedIncomeReturnRate()
                          .compareTo(baselineAssumptions.fixedIncomeReturnRate())
                      < 0;
              boolean adverse =
                  spendingAdverse || growthAdverse || equityAdverse || fixedIncomeAdverse;
              boolean failed = spendingAdverse;
              BigDecimal reserve =
                  spendingAdverse
                      ? new BigDecimal("0.5")
                      : growthAdverse ? new BigDecimal("2.0") : new BigDecimal("3.4");
              BigDecimal wealth =
                  equityAdverse ? new BigDecimal("200000") : new BigDecimal("1000000");
              BigDecimal spendable =
                  adverse && !equityAdverse ? new BigDecimal("900000") : new BigDecimal("1000000");
              PlanSustainabilityAssessment assessment =
                  new PlanSustainabilityAssessment(
                      failed
                          ? PlanSustainabilityStatus.UNSUSTAINABLE
                          : PlanSustainabilityStatus.SUSTAINABLE,
                      failed ? 2058 : null,
                      failed ? 72 : null,
                      failed ? new BigDecimal("1000") : BigDecimal.ZERO,
                      reserve,
                      spendable,
                      wealth);
              return new SimulationEvaluation(null, null, assessment);
            });
    return evaluations;
  }

  private static InvestmentProfile profileWithMarketBuckets() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        new BigDecimal("1000000"),
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.EQUITY,
                new BigDecimal("600000"),
                BigDecimal.ONE,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("400000"),
                BigDecimal.ONE,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (new BigDecimal("1000000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("1000000")),
        new BigDecimal("1000000")
            .subtract(
                (new BigDecimal("1000000") == null
                    ? java.math.BigDecimal.ZERO
                    : new BigDecimal("1000000")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            new BigDecimal("1000000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile profileWithRentalIncome() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static SimulationEvaluation evaluation(
      boolean sustainable, String unfunded, String reserve, String spendable, String wealth) {
    return new SimulationEvaluation(
        null,
        null,
        new PlanSustainabilityAssessment(
            sustainable
                ? PlanSustainabilityStatus.SUSTAINABLE
                : PlanSustainabilityStatus.UNSUSTAINABLE,
            null,
            null,
            new BigDecimal(unfunded),
            new BigDecimal(reserve),
            new BigDecimal(spendable),
            new BigDecimal(wealth),
            true));
  }

  private static InvestmentProfile profileWithRealEstateAssets(boolean mixed) {
    var assets =
        List.of(
            realEstate(1L, mixed ? null : new BigDecimal("0.04")),
            realEstate(2L, new BigDecimal("0.04")));
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        new BigDecimal("200"),
        new BigDecimal("200"),
        BigDecimal.ZERO,
        new BigDecimal("200"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                new BigDecimal("200"),
                BigDecimal.ONE,
                Liquidity.ILLIQUID,
                Liquidity.ILLIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            assets,
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("200"),
            BigDecimal.ZERO,
            new BigDecimal("200")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile profileWithRealEstateAssets(
      List<ProjectedLongTermAsset> assets) {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        new BigDecimal("200"),
        new BigDecimal("200"),
        BigDecimal.ZERO,
        new BigDecimal("200"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                new BigDecimal("200"),
                BigDecimal.ONE,
                Liquidity.ILLIQUID,
                Liquidity.ILLIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            assets,
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("200"),
            BigDecimal.ZERO,
            new BigDecimal("200")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static ProjectedLongTermAsset realEstate(Long id, BigDecimal explicitReturn) {
    return new ProjectedLongTermAsset(
        id,
        "Property " + id,
        com.smartbox.investory.longterm.api.model.LongTermAssetType.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE,
        CurrencyType.PLN,
        new BigDecimal("100"),
        Liquidity.ILLIQUID,
        explicitReturn == null
            ? List.of()
            : List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    explicitReturn,
                    null,
                    false)),
        java.util.List.of(),
        null,
        null,
        null,
        BigDecimal.ZERO,
        null,
        false);
  }
}
