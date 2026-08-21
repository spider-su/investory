package com.smartbox.investory.retirement.simulation;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class RetirementSimulationServiceTest {
  private final RetirementSimulationService service = new RetirementSimulationService();
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Test
  void immediateRetirementWithZeroWorkInputsIsTheLegacyBehavior() {
    var legacy = assumptionsWith(100, 0, 40, List.of());
    var explicit =
        transitionUntil(100, 0, 40, 40, 40, 0, 0)
            .withFundingStrategy(SimulationFundingStrategy.SIMPLE_WATERFALL);
    var legacyResult =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH), legacy, SimulationScenario.BASE);
    var explicitResult =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH), explicit, SimulationScenario.BASE);
    assertEquals(legacyResult.years(), explicitResult.years());
  }

  @Test
  void workingYearsAccumulateExplicitContributionsAndStopAtRetirement() {
    var result =
        service.simulate(
            profile(0, EconomicBucket.LIQUID_CASH),
            transition(0, 0, 40, 45, 20000, 10000),
            SimulationScenario.BASE);
    assertEquals(
        5,
        result.years().stream()
            .filter(y -> y.lifecyclePhase() == SimulationLifecyclePhase.WORKING)
            .count());
    assertBd("50000", result.years().get(4).endNetWorth());
    assertBd("10000", result.years().get(4).preRetirementContribution());
    assertBd("0", result.years().get(5).preRetirementContribution());
    assertEquals(SimulationLifecyclePhase.RETIRED, result.years().get(5).lifecyclePhase());
    assertTrue(result.years().get(5).retirementTransitionYear());
  }

  @Test
  void recurringSpendingExistsBeforeRetirementAndGrowsEachYear() {
    var result =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH),
            transitionWithGrowth(100, 0, 40, 42, new BigDecimal("0.10")),
            SimulationScenario.BASE);
    assertBd("110", result.years().get(1).coreExpenses());
    assertBd("121", result.years().get(2).coreExpenses());
    assertBd("133.1", result.years().get(3).coreExpenses());
  }

  @Test
  void pensionRemainsIndependentAndFundsTheRetirementBridge() {
    var assumptions = transitionUntil(100, 0, 40, 70, 55, 0, 0);
    assumptions = withPension(assumptions, 65, 100);
    var result =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH), assumptions, SimulationScenario.BASE);
    assertBd("100", result.years().get(0).requiredPortfolioFunding());
    assertBd(
        result.years().get(15).coreExpenses().toPlainString(),
        result.years().get(15).requiredPortfolioFunding());
    assertBd("100", result.years().get(25).pensionIncome());
    assertTrue(
        result
                .years()
                .get(25)
                .requiredPortfolioFunding()
                .compareTo(result.years().get(25).coreExpenses())
            < 0);
  }

  @Test
  void decisionReserveCoverageIgnoresWorkingYears() {
    var assumptions = transition(100, 0, 40, 42, 0, 0);
    var summary =
        SimulationDecisionSummary.from(
            service.simulate(
                profile(10000, EconomicBucket.LIQUID_CASH), assumptions, SimulationScenario.BASE),
            assumptions);
    assertTrue(summary.minimumSafeReserveCoverageYears().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void noGrowthScenarioPreservesCapitalWhenThereAreNoExpenses() {
    var result =
        service.simulate(
            profile(1000, EconomicBucket.EQUITY), assumptions(0, 0, 0, 0), SimulationScenario.BASE);
    assertFalse(result.simulationFailed());
    assertBd("1000", result.finalYear().endNetWorth());
  }

  @Test
  void inflationRaisesExpensesEachYear() {
    var result =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH),
            new SimulationAssumptions(
                40,
                41,
                new BigDecimal("100"),
                new BigDecimal("0.10"),
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                99,
                ZERO,
                ZERO),
            SimulationScenario.BASE);
    assertEquals(new BigDecimal("110.00000000"), result.years().get(1).livingExpenses());
  }

  @Test
  void spendingGrowthIsIndependentFromInflation() {
    var lowerInflation =
        new SimulationAssumptions(
            40,
            41,
            new BigDecimal("100000"),
            new BigDecimal("0.05"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            BigDecimal.ZERO,
            List.of(),
            new BigDecimal("0.01"),
            new BigDecimal("0.02"));
    var higherInflation =
        new SimulationAssumptions(
            40,
            41,
            new BigDecimal("100000"),
            new BigDecimal("0.10"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            BigDecimal.ZERO,
            List.of(),
            new BigDecimal("0.01"),
            new BigDecimal("0.02"));
    var lowerResult =
        service.simulate(
            profile(1000000, EconomicBucket.LIQUID_CASH), lowerInflation, SimulationScenario.BASE);
    var higherResult =
        service.simulate(
            profile(1000000, EconomicBucket.LIQUID_CASH), higherInflation, SimulationScenario.BASE);
    assertBd("102000", lowerResult.years().get(1).coreExpenses());
    assertBd("102000", higherResult.years().get(1).coreExpenses());
  }

  @Test
  void coreAndDiscretionarySpendingInflateIndependently() {
    var result =
        service.simulate(
            profile(1000, EconomicBucket.LIQUID_CASH),
            assumptionsWithRental(
                40, 41, new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("0.10"), ZERO),
            SimulationScenario.BASE);
    assertBd("110", result.years().get(1).coreExpenses());
    assertBd("44", result.years().get(1).discretionaryExpenses());
    assertBd("154", result.years().get(1).totalExpenses());
  }

  @Test
  void equityReturnGrowsEquity() {
    var result =
        service.simulate(
            profile(100, EconomicBucket.EQUITY),
            new SimulationAssumptions(
                40, 40, ZERO, ZERO, ZERO, ZERO, new BigDecimal("0.10"), ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertBd("110", result.finalYear().endNetWorth());
  }

  @Test
  void withdrawalUsesCashThenFixedIncomeThenEquity() {
    var p =
        profile(
            Map.of(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("100"),
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("200"),
                EconomicBucket.EQUITY,
                new BigDecimal("300")));
    var r =
        service.simulate(
            p,
            new SimulationAssumptions(
                40, 40, new BigDecimal("150"), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    var y = r.finalYear();
    assertBd("0", y.cashEnd());
    assertBd("150", y.fixedIncomeEnd());
    assertBd("300", y.equityEnd());
  }

  @Test
  void depletionReportsFailureAndUnfundedAmount() {
    var r =
        service.simulate(
            profile(100, EconomicBucket.EQUITY),
            new SimulationAssumptions(
                40, 40, new BigDecimal("200"), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertTrue(r.simulationFailed());
    assertEquals(40, r.failureAge());
    assertBd("100", r.unfundedAmount());
  }

  @Test
  void withdrawalTracksOnlyFundedAssetsAndKeepsShortfallSeparate() {
    var year =
        service
            .simulate(
                profile(100, EconomicBucket.LIQUID_CASH),
                new SimulationAssumptions(
                    40,
                    41,
                    new BigDecimal("150"),
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    99,
                    ZERO,
                    ZERO),
                SimulationScenario.BASE)
            .years()
            .get(0);
    assertBd("150", year.requiredPortfolioFunding());
    assertBd("100", year.actualPortfolioWithdrawal());
    assertBd("50", year.unfundedAmount());
    assertEquals(
        0,
        year.requiredPortfolioFunding()
            .compareTo(year.actualPortfolioWithdrawal().add(year.unfundedAmount())));
  }

  @Test
  void noLiquidityYearHasNoActualWithdrawal() {
    var year =
        service
            .simulate(
                profile(0, EconomicBucket.LIQUID_CASH),
                new SimulationAssumptions(
                    40,
                    40,
                    new BigDecimal("100"),
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    99,
                    ZERO,
                    ZERO),
                SimulationScenario.BASE)
            .finalYear();
    assertBd("100", year.requiredPortfolioFunding());
    assertBd("0", year.actualPortfolioWithdrawal());
    assertBd("100", year.unfundedAmount());
  }

  @Test
  void otherAssetsAreNotReportedAsSpendableOrUsedForWithdrawal() {
    var year =
        service
            .simulate(
                profile(100, EconomicBucket.OTHER),
                new SimulationAssumptions(
                    40,
                    40,
                    new BigDecimal("100"),
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    99,
                    ZERO,
                    ZERO),
                SimulationScenario.BASE)
            .finalYear();
    assertBd("0", year.spendableLiquidAssets());
    assertBd("0", year.actualPortfolioWithdrawal());
    assertBd("100", year.unfundedAmount());
    assertBd("100", year.otherEnd());
  }

  @Test
  void equityIsFinancialButNotSpendableWhenEmergencyWithdrawalIsDisabled() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            new BigDecimal("50"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(),
            ZERO,
            ZERO,
            SimulationFundingStrategy.RESERVE_AND_HARVEST,
            new BigDecimal("5"),
            new BigDecimal("0.07"),
            new BigDecimal("0.75"),
            false);
    SimulationYear year =
        service
            .simulate(profile(100, EconomicBucket.EQUITY), assumptions, SimulationScenario.BASE)
            .finalYear();
    assertBd("0", year.safeReserveEnd());
    assertBd("0", year.spendableAssetsEnd());
    assertBd("100", year.financialAssetsEnd());
    assertTrue(year.failed());
    assertBd("50", year.unfundedAmount());
  }

  @Test
  void realEstateWithoutTaxBaseDoesNotInferTaxFromRent() {
    ProjectedLongTermAsset property =
        new ProjectedLongTermAsset(
            1L,
            "Property",
            LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            Liquidity.ILLIQUID,
            List.of(period(2026, null, "100", CashFlowType.RENT)),
            null,
            null,
            null,
            new BigDecimal("0.085"),
            null);
    assertBd(
        "100",
        service
            .simulate(
                profileWithAssets(List.of(property), new BigDecimal("1000")),
                assumptionsWith(0, 0, 40, List.of()),
                SimulationScenario.BASE)
            .finalYear()
            .passiveIncome());
    ProjectedLongTermAsset taxed =
        new ProjectedLongTermAsset(
            1L,
            "Property",
            LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            Liquidity.ILLIQUID,
            List.of(period(2026, null, "100", CashFlowType.RENT)),
            null,
            null,
            null,
            new BigDecimal("0.085"),
            new BigDecimal("80"));
    assertBd(
        "93.2",
        service
            .simulate(
                profileWithAssets(List.of(taxed), new BigDecimal("1000")),
                assumptionsWith(0, 0, 40, List.of()),
                SimulationScenario.BASE)
            .finalYear()
            .passiveIncome());
  }

  @Test
  void scenarioValidationRejectsEffectiveReturnBelowNegativeOne() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40, 40, ZERO, ZERO, ZERO, ZERO, new BigDecimal("-0.99"), ZERO, ZERO, 99, ZERO, ZERO);
    assertThrows(
        IllegalArgumentException.class,
        () -> SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions));
  }

  @Test
  void manualCashReservesAreWithdrawnByLowestReturnThenId() {
    ProjectedLongTermAsset low =
        new ProjectedLongTermAsset(
            1L,
            "Low",
            LongTermAssetType.CASH_RESERVE,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1), null, ZERO, ZERO, ZERO)),
            null,
            null,
            null,
            ZERO);
    ProjectedLongTermAsset high =
        new ProjectedLongTermAsset(
            2L,
            "High",
            LongTermAssetType.CASH_RESERVE,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1), null, ZERO, ZERO, new BigDecimal("0.04"))),
            null,
            null,
            null,
            ZERO);
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            ZERO,
            new BigDecimal("200"),
            new BigDecimal("200"),
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("200"),
            ZERO,
            List.of(
                new ProfileAllocation(
                    EconomicBucket.LIQUID_CASH,
                    new BigDecimal("200"),
                    BigDecimal.ONE,
                    Liquidity.LIQUID)),
            List.of(low, high));
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            new BigDecimal("50"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(),
            ZERO,
            ZERO,
            SimulationFundingStrategy.RESERVE_AND_HARVEST,
            BigDecimal.ZERO,
            new BigDecimal("0.07"),
            new BigDecimal("0.75"),
            false);
    assertBd(
        "154",
        service
            .simulate(p, assumptions, SimulationScenario.BASE)
            .finalYear()
            .manualLiquidReserveEnd());
  }

  @Test
  void pensionRemovesWithdrawalFromConfiguredAge() {
    var r =
        service.simulate(
            profile(100, EconomicBucket.LIQUID_CASH),
            new SimulationAssumptions(
                40,
                41,
                new BigDecimal("100"),
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                41,
                new BigDecimal("100"),
                ZERO),
            SimulationScenario.BASE);
    assertBd("100", r.years().get(0).requiredPortfolioWithdrawal());
    assertBd("0", r.years().get(1).requiredPortfolioWithdrawal());
  }

  @Test
  void realEstateIsNeverUsedForWithdrawal() {
    var r =
        service.simulate(
            profile(1000, EconomicBucket.REAL_ESTATE),
            new SimulationAssumptions(
                40, 40, new BigDecimal("100"), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertTrue(r.simulationFailed());
    assertBd("0", r.finalYear().realEstateEnd());
  }

  @Test
  void unrealizedCapitalGrowthIsNotTaxedAnnually() {
    var r =
        service.simulate(
            profile(100, EconomicBucket.EQUITY),
            new SimulationAssumptions(
                40,
                40,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                new BigDecimal("0.10"),
                ZERO,
                ZERO,
                99,
                ZERO,
                new BigDecimal("0.20")),
            SimulationScenario.BASE);
    assertBd("110", r.finalYear().endNetWorth());
  }

  @Test
  void scenariosAreDeterministicAndFortyYearProjectionHasExpectedLength() {
    var a =
        new SimulationAssumptions(
            40, 80, ZERO, ZERO, ZERO, ZERO, new BigDecimal("0.06"), ZERO, ZERO, 99, ZERO, ZERO);
    var results = service.compareScenarios(profile(100, EconomicBucket.EQUITY), a);
    assertEquals(41, results.get(SimulationScenario.BASE).years().size());
    assertTrue(
        results
                .get(SimulationScenario.OPTIMISTIC)
                .finalYear()
                .endNetWorth()
                .compareTo(results.get(SimulationScenario.BASE).finalYear().endNetWorth())
            > 0);
    assertTrue(
        results
                .get(SimulationScenario.CONSERVATIVE)
                .finalYear()
                .endNetWorth()
                .compareTo(results.get(SimulationScenario.BASE).finalYear().endNetWorth())
            < 0);
  }

  @Test
  void storedPeriodReturnOverridesGenericBucketRate() {
    InvestmentProfile p = profile(100, EconomicBucket.EQUITY);
    ProjectedLongTermAsset asset =
        new ProjectedLongTermAsset(
            1L,
            "Equity asset",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.OTHER,
            EconomicBucket.EQUITY,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2026, 1, 1),
                    java.time.LocalDate.of(2026, 12, 31),
                    ZERO,
                    ZERO,
                    new BigDecimal("0.10")),
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2027, 1, 1), null, ZERO, ZERO, new BigDecimal("0.20"))),
            null,
            null,
            null,
            ZERO);
    p =
        new InvestmentProfile(
            p.portfolioId(),
            p.currency(),
            ZERO,
            new BigDecimal("100"),
            new BigDecimal("100"),
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("100"),
            ZERO,
            List.of(
                new ProfileAllocation(
                    EconomicBucket.EQUITY,
                    new BigDecimal("100"),
                    BigDecimal.ONE,
                    Liquidity.LIQUID)),
            List.of(asset));
    var a =
        new SimulationAssumptions(
            40, 41, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO, 2026);
    var r = service.simulate(p, a, SimulationScenario.BASE);
    assertBd("110", r.years().get(0).endNetWorth());
    assertBd("132", r.years().get(1).endNetWorth());
  }

  @Test
  void separateRealEstateRatesAreNotSummed() {
    ProjectedLongTermAsset a =
        asset(
            1L,
            "A",
            EconomicBucket.REAL_ESTATE,
            "710000",
            new BigDecimal("0.02"),
            null,
            null,
            null,
            ZERO);
    ProjectedLongTermAsset b =
        asset(
            2L,
            "B",
            EconomicBucket.REAL_ESTATE,
            "780000",
            new BigDecimal("0.015"),
            null,
            null,
            null,
            ZERO);
    var p = profileWithAssets(List.of(a, b), new BigDecimal("1490000"));
    var r =
        service.simulate(
            p,
            new SimulationAssumptions(
                40, 40, ZERO, ZERO, ZERO, ZERO, ZERO, new BigDecimal("0.10"), ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertBd("0", r.finalYear().realEstateEnd());
  }

  @Test
  void payoutBondPaysIncomeKeepsPrincipalLockedUntilRedemption() {
    ProjectedLongTermAsset bond =
        asset(
            1L,
            "Bond",
            EconomicBucket.FIXED_INCOME,
            "100",
            new BigDecimal("0.10"),
            java.time.LocalDate.of(2027, 6, 1),
            null,
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            new BigDecimal("0.20"));
    var r =
        service.simulate(
            profileWithAssets(List.of(bond), new BigDecimal("100")),
            new SimulationAssumptions(
                40, 41, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO, 2026),
            SimulationScenario.BASE);
    assertBd("8", r.years().get(0).passiveIncome());
    assertBd("0", r.years().get(0).fixedIncomeEnd());
    assertBd("100", r.years().get(0).lockedContractualAssets());
    assertBd("8", r.years().get(0).spendableLiquidAssets());
    assertBd("116", r.years().get(1).cashEnd());
    assertBd("0", r.years().get(1).lockedContractualAssets());
  }

  @Test
  void capitalizedBondDoesNotBecomePassiveIncome() {
    ProjectedLongTermAsset bond =
        asset(
            1L,
            "Bond",
            EconomicBucket.FIXED_INCOME,
            "100",
            new BigDecimal("0.10"),
            java.time.LocalDate.of(2027, 6, 1),
            null,
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.CAPITALIZE,
            new BigDecimal("0.20"));
    var r =
        service.simulate(
            profileWithAssets(List.of(bond), new BigDecimal("100")),
            new SimulationAssumptions(
                40, 41, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO, 2026),
            SimulationScenario.BASE);
    assertBd("0", r.years().get(0).passiveIncome());
    assertBd("108", r.years().get(0).lockedContractualAssets());
    assertBd("116.64", r.years().get(1).cashEnd());
  }

  @Test
  void multipleManualFixedIncomeRatesAreNotSummed() {
    ProjectedLongTermAsset first =
        asset(
            1L,
            "Bond A",
            EconomicBucket.FIXED_INCOME,
            "100",
            new BigDecimal("0.10"),
            null,
            null,
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.CAPITALIZE,
            ZERO);
    ProjectedLongTermAsset second =
        asset(
            2L,
            "Bond B",
            EconomicBucket.FIXED_INCOME,
            "100",
            new BigDecimal("0.20"),
            null,
            null,
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.CAPITALIZE,
            ZERO);
    var r =
        service.simulate(
            profileWithAssets(List.of(first, second), new BigDecimal("200")),
            new SimulationAssumptions(
                40, 40, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertBd("230", r.finalYear().lockedContractualAssets());
  }

  @Test
  void depositCapitalizesAndRedeemsOnce() {
    ProjectedLongTermAsset deposit =
        new ProjectedLongTermAsset(
            1L,
            "Deposit",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.DEPOSIT,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, new BigDecimal("0.10"))),
            java.time.LocalDate.of(2027, 6, 1),
            null,
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.CAPITALIZE,
            ZERO);
    var r =
        service.simulate(
            profileWithAssets(List.of(deposit), new BigDecimal("100")),
            new SimulationAssumptions(
                40, 42, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO, 2026),
            SimulationScenario.BASE);
    assertBd("110", r.years().get(0).lockedContractualAssets());
    assertBd("121", r.years().get(1).cashEnd());
    assertBd("121", r.years().get(2).cashEnd());
  }

  @Test
  void maturityYearRedemptionFundsMaturityYearExpenseAndIsNotRepeated() {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            1L,
            "Maturing bond",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, ZERO)),
            java.time.LocalDate.of(2027, 6, 1),
            new BigDecimal("100"),
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            ZERO);
    var assumptions =
        new SimulationAssumptions(
            40,
            42,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(
                new SimulationEvent(
                    null,
                    2027,
                    "Maturity-year expense",
                    new BigDecimal("100"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null),
                new SimulationEvent(
                    null,
                    2028,
                    "Later expense",
                    new BigDecimal("100"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)));
    var result =
        service.simulate(
            profileWithAssets(List.of(bond), new BigDecimal("100")),
            assumptions,
            SimulationScenario.BASE);
    assertFalse(result.years().get(1).failed());
    assertBd("100", result.years().get(1).requiredPortfolioWithdrawal());
    assertBd("100", result.years().get(1).eventExpenses());
    assertBd("0", result.years().get(1).fixedIncomeEnd());
    assertTrue(result.years().get(2).failed());
    assertBd("100", result.years().get(2).unfundedAmount());
  }

  @Test
  void bondMaturityUsesNeedAndReinvestsUnusedPrincipalForThreeYears() {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            77L,
            "Ladder bond",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("200"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, ZERO)),
            java.time.LocalDate.of(2027, 6, 15),
            new BigDecimal("200"),
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            ZERO);
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            47,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(
                new SimulationEvent(
                    null,
                    2027,
                    "maturity-year need",
                    new BigDecimal("80"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)),
            ZERO,
            ZERO,
            SimulationFundingStrategy.RESERVE_AND_HARVEST,
            BigDecimal.ONE,
            new BigDecimal("0.07"),
            new BigDecimal("0.75"),
            true,
            40,
            ZERO,
            ZERO);

    SimulationResult result =
        service.simulate(
            profileWithAssets(List.of(bond), new BigDecimal("200")),
            assumptions,
            SimulationScenario.BASE);

    SimulationYear maturityYear = result.years().get(1);
    assertBd("80", maturityYear.requiredPortfolioFunding());
    assertBd("80", maturityYear.actualPortfolioWithdrawal());
    assertBd("0", maturityYear.fixedIncomeEnd());
    assertBd("120", maturityYear.bondValueEnd());
    assertBd("120", maturityYear.lockedContractualAssets());
    assertBd("0", result.years().get(4).fixedIncomeEnd());
  }

  @Test
  void multipleBondsMaturingInOneYearAreAggregatedBeforeReinvestment() {
    ProjectedLongTermAsset first = ladderTestBond(78L, "100");
    ProjectedLongTermAsset second = ladderTestBond(79L, "150");
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            41,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(
                new SimulationEvent(
                    null,
                    2027,
                    "need",
                    new BigDecimal("80"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)));

    SimulationYear maturityYear =
        service
            .simulate(
                profileWithAssets(List.of(first, second), new BigDecimal("250")),
                assumptions,
                SimulationScenario.BASE)
            .years()
            .get(1);

    assertBd("80", maturityYear.actualPortfolioWithdrawal());
    assertBd("0", maturityYear.fixedIncomeEnd());
    assertBd("170", maturityYear.lockedContractualAssets());
  }

  @Test
  void coreAndDiscretionarySpendingAreReportedSeparately() {
    var a = assumptionsWith(100, 40, 40, List.of());
    var r = service.simulate(profile(1000, EconomicBucket.LIQUID_CASH), a, SimulationScenario.BASE);
    var y = r.finalYear();
    assertBd("100", y.coreExpenses());
    assertBd("40", y.discretionaryExpenses());
    assertBd("140", y.totalExpenses());
    assertBd("140", y.requiredPortfolioWithdrawal());
  }

  @Test
  void expenseEventChangesOnlyItsCalendarYear() {
    var a =
        new SimulationAssumptions(
            40,
            41,
            new BigDecimal("100"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(
                new SimulationEvent(
                    null,
                    2027,
                    "Car",
                    new BigDecimal("150"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)));
    var r = service.simulate(profile(500, EconomicBucket.LIQUID_CASH), a, SimulationScenario.BASE);
    assertBd("0", r.years().get(0).eventExpenses());
    assertBd("150", r.years().get(1).eventExpenses());
    assertBd("250", r.years().get(1).requiredPortfolioWithdrawal());
  }

  @Test
  void incomeEventCreatesLiquidSurplus() {
    var a =
        assumptionsWith(
            100,
            0,
            40,
            List.of(
                new SimulationEvent(
                    null,
                    2026,
                    "Sale proceeds",
                    new BigDecimal("150"),
                    SimulationEventType.ONE_OFF_INCOME,
                    null)));
    var r = service.simulate(profile(0, EconomicBucket.LIQUID_CASH), a, SimulationScenario.BASE);
    var y = r.finalYear();
    assertBd("150", y.eventIncome());
    assertBd("0", y.requiredPortfolioWithdrawal());
    assertBd("50", y.cashEnd());
  }

  @Test
  void severalEventsInOneYearAreAggregatedByType() {
    var a =
        assumptionsWith(
            100,
            0,
            40,
            List.of(
                new SimulationEvent(
                    null,
                    2026,
                    "Car",
                    new BigDecimal("150"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null),
                new SimulationEvent(
                    null,
                    2026,
                    "Bonus",
                    new BigDecimal("50"),
                    SimulationEventType.ONE_OFF_INCOME,
                    null)));
    var r = service.simulate(profile(1000, EconomicBucket.LIQUID_CASH), a, SimulationScenario.BASE);
    var y = r.finalYear();
    assertBd("150", y.eventExpenses());
    assertBd("50", y.eventIncome());
    assertBd("250", y.totalExpenses());
    assertBd("200", y.requiredPortfolioWithdrawal());
  }

  @Test
  void oneOffExpenseCanCausePortfolioFailure() {
    var a =
        assumptionsWith(
            0,
            0,
            40,
            List.of(
                new SimulationEvent(
                    null,
                    2026,
                    "Renovation",
                    new BigDecimal("200"),
                    SimulationEventType.ONE_OFF_EXPENSE,
                    null)));
    var r = service.simulate(profile(100, EconomicBucket.EQUITY), a, SimulationScenario.BASE);
    assertTrue(r.simulationFailed());
    assertBd("100", r.unfundedAmount());
  }

  @Test
  void rentalIncomeGrowsAtConfiguredRateAndReducesWithdrawal() {
    var result =
        service.simulate(
            profileWithAssets(
                List.of(
                    rentalProperty(List.of(period(2026, null, "180000", CashFlowType.RENT)), ZERO)),
                new BigDecimal("1000000")),
            assumptionsWithRental(
                40, 41, new BigDecimal("200000"), ZERO, ZERO, new BigDecimal("0.02")),
            SimulationScenario.BASE);
    assertBd("180000", result.years().get(0).passiveIncome());
    assertBd("20000", result.years().get(0).requiredPortfolioWithdrawal());
    assertBd("183600", result.years().get(1).passiveIncome());
    assertBd("16400", result.years().get(1).requiredPortfolioWithdrawal());
  }

  @Test
  void openEndedRentalUsesCanonicalPeriodIncomeWithZeroGrowth() {
    var property = rentalProperty(List.of(period(2026, null, "50000", CashFlowType.RENT)), ZERO);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            assumptionsWithRental(40, 42, new BigDecimal("262000"), ZERO, ZERO, ZERO),
            SimulationScenario.BASE);

    assertBd("50000", result.years().get(0).rentalIncome());
    assertBd("50000", result.years().get(1).rentalIncome());
    assertBd("50000", result.years().get(2).rentalIncome());
    assertBd("212000", result.years().get(0).requiredPortfolioFunding());
  }

  @Test
  void workingIncomeCoversTheRemainingNeedWithoutChangingExplicitContribution() {
    var property = rentalProperty(List.of(period(2026, null, "50000", CashFlowType.RENT)), ZERO);
    var assumptions = transitionUntil(262000, 0, 40, 41, 41, 240000, 120000);
    var year =
        service
            .simulate(
                profileWithAssets(List.of(property), ZERO), assumptions, SimulationScenario.BASE)
            .years()
            .get(0);

    assertBd("212000", year.incomeGap());
    assertBd("0", year.requiredPortfolioFunding());
    assertBd("120000", year.preRetirementContribution());
  }

  @Test
  void pensionFurtherReducesPortfolioNeedAfterItStarts() {
    var property = rentalProperty(List.of(period(2026, null, "50000", CashFlowType.RENT)), ZERO);
    var assumptions =
        withPension(
            assumptionsWithRental(40, 41, new BigDecimal("262000"), ZERO, ZERO, ZERO), 41, 7000);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), ZERO), assumptions, SimulationScenario.BASE);

    assertBd("212000", result.years().get(0).requiredPortfolioFunding());
    assertBd("205000", result.years().get(1).requiredPortfolioFunding());
  }

  @Test
  void rentalGrowthDoesNotChangeSpendingAndSpendingGrowthDoesNotChangeRent() {
    var property = rentalProperty(List.of(period(2026, null, "50000", CashFlowType.RENT)), ZERO);
    var assumptions =
        new SimulationAssumptions(
            40,
            41,
            new BigDecimal("100000"),
            new BigDecimal("0.05"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            BigDecimal.ZERO,
            List.of(),
            new BigDecimal("0.01"),
            new BigDecimal("0.02"));
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            assumptions,
            SimulationScenario.BASE);
    assertBd("102000", result.years().get(1).coreExpenses());
    assertBd("50500", result.years().get(1).passiveIncome());
  }

  @Test
  void explicitFutureRentResetsRentalGrowthAndGrowthThenResumes() {
    var property =
        rentalProperty(
            List.of(
                period(2026, 2027, "36000", CashFlowType.RENT),
                period(2028, null, "39600", CashFlowType.RENT)),
            ZERO);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            assumptionsWithRental(40, 43, ZERO, ZERO, ZERO, new BigDecimal("0.02")),
            SimulationScenario.BASE);
    assertBd("36000", result.years().get(0).passiveIncome());
    assertBd("36720", result.years().get(1).passiveIncome());
    assertBd("39600", result.years().get(2).passiveIncome());
    assertBd("40392", result.years().get(3).passiveIncome());
  }

  @Test
  void rentalIncomeTypesGrowButApartmentExpensesDoNot() {
    var property =
        rentalProperty(
            List.of(
                period(2026, null, "120000", CashFlowType.RENT),
                period(2026, null, "12000", CashFlowType.PARKING_RENT),
                period(2026, null, "6000", CashFlowType.OTHER_INCOME),
                period(2026, null, "10000", CashFlowType.ADMIN_FEE),
                period(2026, null, "5000", CashFlowType.UTILITIES),
                period(2026, null, "2000", CashFlowType.PROPERTY_TAX),
                period(2026, null, "3000", CashFlowType.INSURANCE)),
            ZERO);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            assumptionsWithRental(40, 41, ZERO, ZERO, ZERO, new BigDecimal("0.02")),
            SimulationScenario.BASE);
    assertBd("118000", result.years().get(0).passiveIncome());
    assertBd("120760", result.years().get(1).passiveIncome());
  }

  @Test
  void rentalIncomeGrowthIsIndependentFromPropertyValueGrowth() {
    var property =
        rentalProperty(
            List.of(
                period(2026, null, "120000", CashFlowType.RENT),
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1), null, ZERO, ZERO, new BigDecimal("0.01"))),
            ZERO);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            assumptionsWithRental(40, 41, ZERO, ZERO, ZERO, new BigDecimal("0.02")),
            SimulationScenario.BASE);
    assertBd("0", result.years().get(0).realEstateEnd());
    assertBd("0", result.years().get(1).realEstateEnd());
    assertBd("120000", result.years().get(0).passiveIncome());
    assertBd("122400", result.years().get(1).passiveIncome());
  }

  @Test
  void scenariosAdjustRentalAndSpendingGrowthAroundBase() {
    var assumptions =
        new SimulationAssumptions(
            40,
            40,
            ZERO,
            new BigDecimal("0.03"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(),
            new BigDecimal("0.02"),
            new BigDecimal("0.025"));
    assertBd(
        "0.015",
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions)
            .rentalIncomeGrowthRate());
    assertBd(
        "0.02",
        SimulationScenarioSettings.forScenario(SimulationScenario.BASE, assumptions)
            .rentalIncomeGrowthRate());
    assertBd(
        "0.025",
        SimulationScenarioSettings.forScenario(SimulationScenario.OPTIMISTIC, assumptions)
            .rentalIncomeGrowthRate());
    assertBd(
        "0.030",
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions)
            .spendingGrowthRate());
    assertBd(
        "0.025",
        SimulationScenarioSettings.forScenario(SimulationScenario.BASE, assumptions)
            .spendingGrowthRate());
    assertBd(
        "0.020",
        SimulationScenarioSettings.forScenario(SimulationScenario.OPTIMISTIC, assumptions)
            .spendingGrowthRate());
  }

  @Test
  void originalPlanningModelFixtureUsesReserveAndHarvestPolicy() {
    ProjectedLongTermAsset property =
        rentalProperty(List.of(period(2026, null, "180000", CashFlowType.RENT)), ZERO);
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1520000"),
            new BigDecimal("1000000"),
            new BigDecimal("2520000"),
            ZERO,
            new BigDecimal("180000"),
            new BigDecimal("180000"),
            new BigDecimal("1520000"),
            new BigDecimal("1000000"),
            List.of(
                new ProfileAllocation(
                    EconomicBucket.FIXED_INCOME, new BigDecimal("900000"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.EQUITY, new BigDecimal("620000"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.REAL_ESTATE,
                    new BigDecimal("1000000"),
                    ZERO,
                    Liquidity.ILLIQUID)),
            List.of(property));
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            44,
            new BigDecimal("200000"),
            new BigDecimal("0.025"),
            ZERO,
            ZERO,
            new BigDecimal("0.08"),
            ZERO,
            ZERO,
            99,
            ZERO,
            ZERO,
            2026,
            ZERO,
            List.of(),
            new BigDecimal("0.02"),
            new BigDecimal("0.025"),
            SimulationFundingStrategy.RESERVE_AND_HARVEST,
            new BigDecimal("5"),
            new BigDecimal("0.07"),
            new BigDecimal("0.75"),
            true);
    var result = service.simulate(profile, assumptions, SimulationScenario.BASE);
    assertBd("200000", result.years().get(0).totalExpenses());
    assertBd("205000", result.years().get(1).totalExpenses());
    assertBd("180000", result.years().get(0).passiveIncome());
    assertBd("183600", result.years().get(1).passiveIncome());
    assertBd("20000", result.years().get(0).requiredPortfolioFunding());
    assertBd("21400", result.years().get(1).requiredPortfolioFunding());
    assertBd("20000", result.years().get(0).actualPortfolioWithdrawal());
    assertBd("21400", result.years().get(1).actualPortfolioWithdrawal());
    assertBd("100000", result.years().get(0).safeReserveTarget());
    assertBd("880000", result.years().get(0).fixedIncomeEnd());
    assertBd("858600", result.years().get(1).fixedIncomeEnd());
    assertBd("37200", result.years().get(0).bondValueEnd());
    assertBd("75144", result.years().get(1).bondValueEnd());
    assertBd("632400", result.years().get(0).equityEnd());
    assertBd("645048", result.years().get(1).equityEnd());
    assertBd("37200", result.years().get(0).equityToFixedIncomeTransfer());
    assertBd("0", result.years().get(0).unfundedAmount());
    assertBd("0", result.years().get(1).unfundedAmount());
  }

  @Test
  void reserveHarvestRefillsCombinedDefensiveReserveWithEligibleGain() {
    ProjectedLongTermAsset bond = ladderTestBond(700L, "200");
    SimulationAssumptions assumptions =
        transitionUntil(100, 0, 40, 40, 40, 0, 0)
            .withEquityReturnRate(new BigDecimal("0.10"))
            .withSafeReserveYears(new BigDecimal("3"));

    SimulationYear year =
        service
            .simulate(
                profileWithBondAndEquity(bond, new BigDecimal("2000"), new BigDecimal("200")),
                assumptions,
                SimulationScenario.BASE)
            .finalYear();
    assertBd("300", year.bondValueEnd());
    assertBd("300", year.lockedContractualAssets());
  }

  @Test
  void sufficientCombinedDefensiveReserveDoesNotHarvestIntoCashOrBonds() {
    ProjectedLongTermAsset bond = ladderTestBond(701L, "200");
    SimulationAssumptions assumptions =
        transitionUntil(100, 0, 40, 40, 40, 0, 0)
            .withEquityReturnRate(new BigDecimal("0.10"))
            .withSafeReserveYears(new BigDecimal("0.30"));

    SimulationYear year =
        service
            .simulate(
                profileWithBondAndEquity(bond, new BigDecimal("2000"), new BigDecimal("200")),
                assumptions,
                SimulationScenario.BASE)
            .finalYear();
    assertBd("0", year.equityToFixedIncomeTransfer());
    assertBd("200", year.bondValueEnd());
    assertBd("0", year.fixedIncomeEnd());
  }

  @Test
  void bondHarvestDoesNotRunForSimpleWaterfallOrWorkingYears() {
    ProjectedLongTermAsset bond = ladderTestBond(702L, "200");
    SimulationAssumptions retiredSimple =
        transitionUntil(100, 0, 40, 40, 40, 0, 0)
            .withEquityReturnRate(new BigDecimal("0.10"))
            .withFundingStrategy(SimulationFundingStrategy.SIMPLE_WATERFALL);
    SimulationAssumptions working =
        transitionUntil(100, 0, 40, 41, 41, 0, 0).withEquityReturnRate(new BigDecimal("0.10"));

    assertBd(
        "200",
        service
            .simulate(
                profileWithBondAndEquity(bond, new BigDecimal("2000"), new BigDecimal("200")),
                retiredSimple,
                SimulationScenario.BASE)
            .finalYear()
            .bondValueEnd());
    assertBd(
        "200",
        service
            .simulate(
                profileWithBondAndEquity(bond, new BigDecimal("2000"), new BigDecimal("200")),
                working,
                SimulationScenario.BASE)
            .years()
            .getFirst()
            .bondValueEnd());
  }

  @Test
  void propertyRatesGrowGraduallyAndExplicitPeriodsOverrideScenario() {
    ProjectedLongTermAsset property =
        asset(
            1L,
            "Property",
            EconomicBucket.REAL_ESTATE,
            "1000000",
            new BigDecimal("0.01"),
            null,
            null,
            null,
            ZERO);
    var onePercent =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("1000000")),
            new SimulationAssumptions(
                40,
                41,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                new BigDecimal("0.50"),
                ZERO,
                99,
                ZERO,
                ZERO,
                2026),
            SimulationScenario.BASE);
    assertBd("0", onePercent.years().get(0).realEstateEnd());
    assertBd("0", onePercent.years().get(1).realEstateEnd());
    ProjectedLongTermAsset fallback =
        asset(2L, "Fallback", EconomicBucket.REAL_ESTATE, "1000000", ZERO, null, null, null, ZERO);
    assertBd(
        "0",
        service
            .simulate(
                profileWithAssets(List.of(fallback), new BigDecimal("1000000")),
                new SimulationAssumptions(
                    40,
                    40,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    new BigDecimal("0.02"),
                    ZERO,
                    99,
                    ZERO,
                    ZERO),
                SimulationScenario.BASE)
            .finalYear()
            .realEstateEnd());
    assertBd(
        "0",
        service
            .simulate(
                profileWithAssets(List.of(fallback), new BigDecimal("1000000")),
                new SimulationAssumptions(
                    40,
                    40,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    new BigDecimal("-0.02"),
                    ZERO,
                    99,
                    ZERO,
                    ZERO),
                SimulationScenario.BASE)
            .finalYear()
            .realEstateEnd());
  }

  @Test
  void sanitizedPropertyPortfolioDoesNotDoubleAtTwoPercentGrowth() {
    ProjectedLongTermAsset property =
        asset(
            1L,
            "Property",
            EconomicBucket.REAL_ESTATE,
            "3650000",
            new BigDecimal("0.02"),
            null,
            null,
            null,
            ZERO);
    var result =
        service.simulate(
            profileWithAssets(List.of(property), new BigDecimal("3650000")),
            new SimulationAssumptions(
                40, 42, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertBd("0", result.years().get(0).realEstateEnd());
    assertBd("0", result.years().get(1).realEstateEnd());
    assertBd("0", result.years().get(2).realEstateEnd());
  }

  @Test
  void fivePropertiesAtOnePercentGrowByOnePercentRatherThanDoubling() {
    var properties =
        List.of(
            asset(
                1L,
                "A",
                EconomicBucket.REAL_ESTATE,
                "710000",
                new BigDecimal("0.01"),
                null,
                null,
                null,
                ZERO),
            asset(
                2L,
                "B",
                EconomicBucket.REAL_ESTATE,
                "710000",
                new BigDecimal("0.01"),
                null,
                null,
                null,
                ZERO),
            asset(
                3L,
                "C",
                EconomicBucket.REAL_ESTATE,
                "700000",
                new BigDecimal("0.01"),
                null,
                null,
                null,
                ZERO),
            asset(
                4L,
                "D",
                EconomicBucket.REAL_ESTATE,
                "780000",
                new BigDecimal("0.01"),
                null,
                null,
                null,
                ZERO),
            asset(
                5L,
                "E",
                EconomicBucket.REAL_ESTATE,
                "750000",
                new BigDecimal("0.01"),
                null,
                null,
                null,
                ZERO));
    var result =
        service.simulate(
            profileWithAssets(properties, new BigDecimal("3650000")),
            new SimulationAssumptions(
                40,
                40,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                new BigDecimal("0.025"),
                ZERO,
                99,
                ZERO,
                ZERO),
            SimulationScenario.BASE);
    assertBd("0", result.finalYear().realEstateEnd());
  }

  @Test
  void lockedManualBondCanCauseFailureWhileStillCountingInNetWorthAndRedeemsAtMaturity() {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            1L,
            "Locked bond",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("200000"),
            Liquidity.LIQUID,
            List.of(),
            java.time.LocalDate.of(2027, 6, 1),
            new BigDecimal("200000"),
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            ZERO);
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("70000"),
            new BigDecimal("200000"),
            new BigDecimal("270000"),
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("70000"),
            new BigDecimal("200000"),
            List.of(
                new ProfileAllocation(
                    EconomicBucket.LIQUID_CASH, new BigDecimal("20000"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.EQUITY, new BigDecimal("50000"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.FIXED_INCOME, new BigDecimal("200000"), ZERO, Liquidity.LIQUID)),
            List.of(bond));
    var result =
        service.simulate(
            profile,
            new SimulationAssumptions(
                40,
                41,
                new BigDecimal("100000"),
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                99,
                ZERO,
                ZERO,
                2026),
            SimulationScenario.BASE);
    SimulationYear first = result.years().get(0);
    SimulationYear second = result.years().get(1);
    assertTrue(first.failed());
    assertBd("30000", first.unfundedAmount());
    assertBd("0", first.spendableLiquidAssets());
    assertBd("200000", first.lockedContractualAssets());
    assertBd("200000", first.endNetWorth());
    assertTrue(second.failed());
    assertBd("0", second.unfundedAmount());
    assertBd("0", second.cashEnd());
    assertBd("100000", second.lockedContractualAssets());
    assertBd("100000", second.endNetWorth());
    assertBd("30000", result.firstFailureShortfall());
    assertBd("30000", result.totalUnfundedAmount());
  }

  @Test
  void totalUnfundedAmountSumsEachYearlyShortfall() {
    var result =
        service.simulate(
            profile(0, EconomicBucket.LIQUID_CASH),
            new SimulationAssumptions(
                40, 41, new BigDecimal("100"), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO),
            SimulationScenario.BASE);
    assertBd("100", result.firstFailureShortfall());
    assertBd("200", result.totalUnfundedAmount());
  }

  @Test
  void mixedPortfolioCountsMarketAndManualBalancesExactlyOnce() {
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("200"),
            Liquidity.LIQUID,
            List.of(),
            java.time.LocalDate.of(2028, 1, 1),
            new BigDecimal("200"),
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            ZERO);
    ProjectedLongTermAsset deposit =
        new ProjectedLongTermAsset(
            2L,
            "Deposit",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.DEPOSIT,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(),
            java.time.LocalDate.of(2028, 1, 1),
            new BigDecimal("100"),
            com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
            ZERO);
    ProjectedLongTermAsset property =
        new ProjectedLongTermAsset(
            3L,
            "Property",
            com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("300"),
            Liquidity.ILLIQUID,
            List.of(),
            null,
            null,
            null,
            ZERO);
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("90"),
            new BigDecimal("600"),
            new BigDecimal("690"),
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("90"),
            new BigDecimal("600"),
            List.of(
                new ProfileAllocation(
                    EconomicBucket.LIQUID_CASH, new BigDecimal("120"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.FIXED_INCOME, new BigDecimal("230"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.EQUITY, new BigDecimal("40"), ZERO, Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.REAL_ESTATE, new BigDecimal("300"), ZERO, Liquidity.ILLIQUID)),
            List.of(bond, deposit, property));
    SimulationYear year =
        service
            .simulate(
                profile,
                new SimulationAssumptions(
                    40, 40, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 99, ZERO, ZERO, 2026),
                SimulationScenario.BASE)
            .finalYear();
    assertBd("90", year.spendableLiquidAssets());
    assertBd("300", year.lockedContractualAssets());
    assertBd("0", year.realEstateEnd());
    assertBd("390", year.endNetWorth());
    assertBd(
        "390",
        year.cashEnd()
            .add(year.fixedIncomeEnd())
            .add(year.equityEnd())
            .add(year.otherEnd())
            .add(year.lockedContractualAssets())
            .add(year.realEstateEnd()));
  }

  private static SimulationAssumptions assumptions(int age, int end, int expenses, int inflation) {
    return new SimulationAssumptions(
        40,
        40,
        BigDecimal.valueOf(expenses),
        BigDecimal.valueOf(inflation),
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        99,
        ZERO,
        ZERO);
  }

  private static SimulationAssumptions transition(
      int expenses,
      int discretionary,
      int currentAge,
      int retirementAge,
      int employmentIncome,
      int contribution) {
    return transitionWithGrowth(
            expenses,
            discretionary,
            currentAge,
            retirementAge,
            SimulationAssumptions.DEFAULT_SPENDING_GROWTH_RATE)
        .withAnnualEmploymentIncome(BigDecimal.valueOf(employmentIncome))
        .withAnnualPreRetirementContribution(BigDecimal.valueOf(contribution));
  }

  private static SimulationAssumptions transitionWithGrowth(
      int expenses, int discretionary, int currentAge, int retirementAge, BigDecimal growth) {
    return transitionUntil(
        expenses, discretionary, currentAge, retirementAge + 1, retirementAge, 0, 0, growth);
  }

  private static SimulationAssumptions transitionUntil(
      int expenses,
      int discretionary,
      int currentAge,
      int endAge,
      int retirementAge,
      int employmentIncome,
      int contribution) {
    return transitionUntil(
        expenses,
        discretionary,
        currentAge,
        endAge,
        retirementAge,
        employmentIncome,
        contribution,
        SimulationAssumptions.DEFAULT_SPENDING_GROWTH_RATE);
  }

  private static SimulationAssumptions transitionUntil(
      int expenses,
      int discretionary,
      int currentAge,
      int endAge,
      int retirementAge,
      int employmentIncome,
      int contribution,
      BigDecimal growth) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        BigDecimal.valueOf(expenses),
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        Integer.MAX_VALUE,
        ZERO,
        ZERO,
        2026,
        BigDecimal.valueOf(discretionary),
        List.of(),
        ZERO,
        growth,
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        BigDecimal.ONE,
        new BigDecimal("0.07"),
        new BigDecimal("0.75"),
        true,
        retirementAge,
        BigDecimal.valueOf(employmentIncome),
        BigDecimal.valueOf(contribution));
  }

  private static SimulationAssumptions withPension(
      SimulationAssumptions base, int pensionStartAge, int annualPension) {
    return new SimulationAssumptions(
        base.currentAge(),
        base.endAge(),
        base.annualLivingExpenses(),
        base.inflationRate(),
        base.cashReturnRate(),
        base.fixedIncomeReturnRate(),
        base.equityReturnRate(),
        base.realEstateReturnRate(),
        base.otherReturnRate(),
        pensionStartAge,
        BigDecimal.valueOf(annualPension),
        base.capitalGainTaxRate(),
        base.startYear(),
        base.annualDiscretionaryExpenses(),
        base.futureEvents(),
        base.rentalIncomeGrowthRate(),
        base.spendingGrowthRate(),
        base.fundingStrategy(),
        base.safeReserveYears(),
        base.equityHarvestMinimumReturnRate(),
        base.equityGainHarvestRate(),
        base.allowEmergencyEquityWithdrawal(),
        base.retirementAge(),
        base.annualEmploymentIncome(),
        base.annualPreRetirementContribution());
  }

  private static SimulationAssumptions assumptionsWith(
      int core, int discretionary, int age, List<SimulationEvent> events) {
    return new SimulationAssumptions(
        age,
        age,
        BigDecimal.valueOf(core),
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        99,
        ZERO,
        ZERO,
        2026,
        BigDecimal.valueOf(discretionary),
        events);
  }

  private static SimulationAssumptions assumptionsWithRental(
      int age,
      int end,
      BigDecimal core,
      BigDecimal discretionary,
      BigDecimal inflation,
      BigDecimal rentalGrowth) {
    return new SimulationAssumptions(
        age,
        end,
        core,
        inflation,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        99,
        ZERO,
        ZERO,
        2026,
        discretionary,
        List.of(),
        rentalGrowth);
  }

  private static InvestmentProfile profile(double value, EconomicBucket bucket) {
    return profile(Map.of(bucket, BigDecimal.valueOf(value)));
  }

  private static InvestmentProfile profile(Map<EconomicBucket, BigDecimal> values) {
    BigDecimal total = values.values().stream().reduce(ZERO, BigDecimal::add);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        total,
        ZERO,
        total,
        ZERO,
        ZERO,
        ZERO,
        values.entrySet().stream()
            .filter(e -> e.getKey() != EconomicBucket.REAL_ESTATE)
            .map(Map.Entry::getValue)
            .reduce(ZERO, BigDecimal::add),
        values.getOrDefault(EconomicBucket.REAL_ESTATE, ZERO),
        values.entrySet().stream()
            .map(
                e ->
                    new ProfileAllocation(
                        e.getKey(),
                        e.getValue(),
                        total.signum() == 0
                            ? ZERO
                            : e.getValue().divide(total, 8, java.math.RoundingMode.HALF_UP),
                        e.getKey() == EconomicBucket.REAL_ESTATE
                            ? Liquidity.ILLIQUID
                            : Liquidity.LIQUID))
            .toList(),
        List.of());
  }

  private static InvestmentProfile profileWithAssets(
      List<ProjectedLongTermAsset> assets, BigDecimal total) {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        ZERO,
        total,
        total,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        total,
        List.of(
            new ProfileAllocation(
                assets.get(0).bucket(), total, BigDecimal.ONE, assets.get(0).liquidity())),
        assets);
  }

  private static InvestmentProfile profileWithBondAndEquity(
      ProjectedLongTermAsset bond, BigDecimal equity, BigDecimal bondValue) {
    BigDecimal total = equity.add(bondValue);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        total,
        ZERO,
        total,
        ZERO,
        ZERO,
        ZERO,
        total,
        ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                bondValue,
                bondValue.divide(total, 8, java.math.RoundingMode.HALF_UP),
                Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY,
                equity,
                equity.divide(total, 8, java.math.RoundingMode.HALF_UP),
                Liquidity.LIQUID)),
        List.of(bond));
  }

  private static ProjectedLongTermAsset asset(
      Long id,
      String name,
      EconomicBucket bucket,
      String value,
      BigDecimal rate,
      java.time.LocalDate maturity,
      BigDecimal redemption,
      com.smartbox.investory.longterm.infrastructure.InterestTreatment treatment,
      BigDecimal tax) {
    return new ProjectedLongTermAsset(
        id,
        name,
        com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.OTHER,
        bucket,
        CurrencyType.PLN,
        new BigDecimal(value),
        bucket == EconomicBucket.REAL_ESTATE ? Liquidity.ILLIQUID : Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, rate)),
        maturity,
        redemption,
        treatment,
        tax);
  }

  private static ProjectedLongTermAsset ladderTestBond(Long id, String value) {
    return new ProjectedLongTermAsset(
        id,
        "Bond " + id,
        com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.PLN,
        new BigDecimal(value),
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, ZERO)),
        java.time.LocalDate.of(2027, 6, 15),
        new BigDecimal(value),
        com.smartbox.investory.longterm.infrastructure.InterestTreatment.PAY_OUT,
        ZERO);
  }

  private static ProjectedLongTermAsset rentalProperty(
      List<ProjectedLongTermAsset.Period> periods, BigDecimal taxRate) {
    return new ProjectedLongTermAsset(
        1L,
        "Property",
        LongTermAssetType.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE,
        CurrencyType.PLN,
        new BigDecimal("1000000"),
        Liquidity.ILLIQUID,
        periods,
        null,
        null,
        null,
        taxRate);
  }

  private static ProjectedLongTermAsset.Period period(
      int from, Integer to, String annualAmount, CashFlowType type) {
    boolean income =
        type == CashFlowType.RENT
            || type == CashFlowType.PARKING_RENT
            || type == CashFlowType.OTHER_INCOME;
    return new ProjectedLongTermAsset.Period(
        LocalDate.of(from, 1, 1),
        to == null ? null : LocalDate.of(to, 12, 31),
        income ? new BigDecimal(annualAmount) : ZERO,
        income ? ZERO : new BigDecimal(annualAmount),
        ZERO,
        type);
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
