package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationSensitivityAnalysisServiceTest {
  @Test
  void failureRiskRanksAheadOfWealthOnlyImpact() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile = profileWithMarketBuckets();
    SimulationEvaluationService evaluations = mockEvaluations(assumptions);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(evaluations).analyze(profile, assumptions);

    assertEquals(SensitivityDriver.RECURRING_SPENDING, result.drivers().get(0).driver());
    assertEquals(
        SensitivityDriverCategory.POLICY_LEVER, SensitivityDriver.RECURRING_SPENDING.category());
    assertEquals(SensitivityDriverCategory.RISK, SensitivityDriver.EQUITY_RETURN.category());
    assertEquals(SensitivityImpact.CRITICAL, result.drivers().get(0).impact());
    assertTrue(
        result.drivers().stream()
            .anyMatch(
                driver ->
                    driver.driver() == SensitivityDriver.EQUITY_RETURN
                        && driver.impact() == SensitivityImpact.WEALTH_ONLY));
    verify(evaluations, atLeast(1)).evaluate(eq(profile), any(), eq(SimulationScenario.BASE));
  }

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
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    assertEquals(
        List.of(
            SensitivityDriver.RECURRING_SPENDING,
            SensitivityDriver.SPENDING_GROWTH,
            SensitivityDriver.SAFE_RESERVE_YEARS),
        result.drivers().stream().map(SimulationSensitivityResult::driver).toList());
  }

  @Test
  void reservePolicyIsAPlanningLeverNotAnExternalRisk() {
    assertEquals(
        SensitivityDriverCategory.POLICY_LEVER, SensitivityDriver.SAFE_RESERVE_YEARS.category());
  }

  @Test
  void mixedRealEstateOverridesDoNotCreateMarketReturnRisk() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile = profileWithRealEstateAssets(true);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    assertTrue(
        result.drivers().stream()
            .noneMatch(driver -> driver.driver() == SensitivityDriver.REAL_ESTATE_RETURN));
  }

  @Test
  void allRealEstateOverridesOmitGlobalReturnRisk() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    InvestmentProfile profile = profileWithRealEstateAssets(false);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    assertTrue(
        result.drivers().stream()
            .noneMatch(driver -> driver.driver() == SensitivityDriver.REAL_ESTATE_RETURN));
  }

  @Test
  void partialExplicitRealEstatePeriodDoesNotCreateMarketReturnRisk() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    ProjectedLongTermAsset asset =
        new ProjectedLongTermAsset(
            1L,
            "Property",
            com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.ILLIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2030, 12, 31),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("0.04"))),
            null,
            null,
            null,
            BigDecimal.ZERO);
    InvestmentProfile profile = profileWithRealEstateAssets(List.of(asset));

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profile, assumptions);

    assertTrue(
        result.drivers().stream()
            .noneMatch(driver -> driver.driver() == SensitivityDriver.REAL_ESTATE_RETURN));
  }

  @Test
  void globalRealEstatePeriodBeforeExplicitOverrideDoesNotCreateMarketReturnRisk() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    ProjectedLongTermAsset asset =
        new ProjectedLongTermAsset(
            1L,
            "Property",
            com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.ILLIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2030, 12, 31),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO),
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2031, 1, 1),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("0.04"))),
            null,
            null,
            null,
            BigDecimal.ZERO);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profileWithRealEstateAssets(List.of(asset)), assumptions);

    assertTrue(
        result.drivers().stream()
            .noneMatch(driver -> driver.driver() == SensitivityDriver.REAL_ESTATE_RETURN));
  }

  @Test
  void globalRealEstateRiskIsNotAddedForAssetsWithoutForwardValue() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80);
    ProjectedLongTermAsset asset =
        new ProjectedLongTermAsset(
            1L,
            "Historical property",
            com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            Liquidity.ILLIQUID,
            List.of(),
            null,
            null,
            null,
            BigDecimal.ZERO);

    SimulationSensitivityAnalysis result =
        new SimulationSensitivityAnalysisService(mockEvaluations(assumptions))
            .analyze(profileWithRealEstateAssets(List.of(asset)), assumptions);

    assertTrue(
        result.drivers().stream()
            .noneMatch(driver -> driver.driver() == SensitivityDriver.REAL_ESTATE_RETURN));
  }

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
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
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
                          .spendingGrowthRate()
                          .compareTo(baselineAssumptions.spendingGrowthRate())
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
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.EQUITY, new BigDecimal("600000"), BigDecimal.ONE, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("400000"),
                BigDecimal.ONE,
                Liquidity.LIQUID)),
        List.of());
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
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("200"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                new BigDecimal("200"),
                BigDecimal.ONE,
                Liquidity.ILLIQUID)),
        assets);
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
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("200"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                new BigDecimal("200"),
                BigDecimal.ONE,
                Liquidity.ILLIQUID)),
        assets);
  }

  private static ProjectedLongTermAsset realEstate(Long id, BigDecimal explicitReturn) {
    return new ProjectedLongTermAsset(
        id,
        "Property " + id,
        com.smartbox.investory.longterm.api.LongTermAssetType.REAL_ESTATE,
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
                    explicitReturn)),
        null,
        null,
        null,
        BigDecimal.ZERO);
  }
}
