package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentYearProjectionBridgeTest {
  @Test
  void rentalIncomeIsAppliedToTheCurrentYearBridgeOnlyBeforeForwardYear() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    ProjectedLongTermAsset rental =
        new ProjectedLongTermAsset(
            21L,
            "Rental",
            LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            Liquidity.ILLIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    null,
                    new BigDecimal("180000"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    CashFlowType.RENT)),
            null,
            null,
            null,
            new BigDecimal("0.085"));
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
            List.of(
                new ProfileAllocation(
                    EconomicBucket.REAL_ESTATE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Liquidity.ILLIQUID)),
            List.of(rental));

    CurrentYearBridgeResult result =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock)
                .create(profile, assumptionsWithRetirement()));

    BigDecimal fraction =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    assertEquals(
        0, new BigDecimal("180000").multiply(fraction).compareTo(result.passiveIncomeUsed()));
    assertEquals(1, result.bridgedProfile().longTermAssets().size());
  }

  @Test
  void currentYearBridgeUsesEffectiveDatedRentalPeriodsBeforeForwardCarry() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    ProjectedLongTermAsset rental =
        new ProjectedLongTermAsset(
            22L,
            "Rental",
            LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            Liquidity.ILLIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 6, 30),
                    new BigDecimal("24000"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    CashFlowType.RENT),
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 7, 1),
                    null,
                    new BigDecimal("27600"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    CashFlowType.RENT)),
            null,
            null,
            null,
            BigDecimal.ZERO);
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
            List.of(rental));

    CurrentYearBridgeResult result =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock)
                .create(profile, assumptionsWithRetirement()));
    BigDecimal currentYearAverage =
        new BigDecimal("24000")
            .multiply(
                new BigDecimal("181")
                    .divide(new BigDecimal("365"), 18, java.math.RoundingMode.HALF_UP))
            .add(
                new BigDecimal("27600")
                    .multiply(
                        new BigDecimal("184")
                            .divide(new BigDecimal("365"), 18, java.math.RoundingMode.HALF_UP)));
    BigDecimal remaining =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    assertEquals(0, currentYearAverage.multiply(remaining).compareTo(result.passiveIncomeUsed()));
  }

  @Test
  void appliesOnlyTheRemainingYearFractionBeforeNextYearsProjection() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    InvestmentProfile current =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            new BigDecimal("1100"),
            BigDecimal.ZERO,
            new BigDecimal("1100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1100"),
            BigDecimal.ZERO,
            List.of(
                new ProfileAllocation(
                    EconomicBucket.LIQUID_CASH,
                    new BigDecimal("100"),
                    BigDecimal.ZERO,
                    Liquidity.LIQUID),
                new ProfileAllocation(
                    EconomicBucket.EQUITY,
                    new BigDecimal("1000"),
                    BigDecimal.ZERO,
                    Liquidity.LIQUID)),
            List.of());
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            42,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.12"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    InvestmentProfile end = bridge.projectCurrentYearEnd(current, assumptions);
    BigDecimal fraction =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    BigDecimal expectedEquity =
        new BigDecimal("1000")
            .multiply(BigDecimal.ONE.add(new BigDecimal("0.12").multiply(fraction)));
    BigDecimal actualEquity =
        end.allocations().stream()
            .filter(a -> a.bucket() == EconomicBucket.EQUITY)
            .findFirst()
            .orElseThrow()
            .value();
    assertEquals(0, expectedEquity.compareTo(actualEquity));
  }

  @Test
  void appliesWorkingContributionOnceToTheCurrentYearBridge() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
                40,
                42,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                99,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2026)
            .withRetirementAge(42)
            .withAnnualPreRetirementContribution(new BigDecimal("120000"));

    CurrentYearBridgeResult result =
        bridge.projectCurrentYearEnd(
            new com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory(clock)
                .create(profile(), assumptions));

    BigDecimal fraction =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    assertEquals(
        0, new BigDecimal("120000").multiply(fraction).compareTo(result.contributionApplied()));
    BigDecimal cash =
        result.bridgedProfile().allocations().stream()
            .filter(a -> a.bucket() == EconomicBucket.LIQUID_CASH)
            .findFirst()
            .orElseThrow()
            .value();
    assertEquals(0, new BigDecimal("100").add(result.contributionApplied()).compareTo(cash));
    assertEquals(SimulationLifecyclePhase.WORKING, result.lifecyclePhase());
  }

  @Test
  void appliesCurrentYearRetirementSpendingAndEventOnlyInTheBridge() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
                40,
                42,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                99,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2026,
                BigDecimal.ZERO,
                List.of(
                    new SimulationEvent(
                        7L,
                        2026,
                        "Event",
                        new BigDecimal("25"),
                        SimulationEventType.ONE_OFF_EXPENSE,
                        null)))
            .withRetirementAge(40);
    ForwardSimulationContext context =
        new com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory(clock)
            .create(profile(), assumptions);
    CurrentYearBridgeResult result = bridge.projectCurrentYearEnd(context);

    assertEquals(SimulationLifecyclePhase.RETIRED, result.lifecyclePhase());
    BigDecimal fraction =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    assertEquals(
        0,
        new BigDecimal("25")
            .add(new BigDecimal("100").multiply(fraction))
            .compareTo(result.requiredPortfolioFunding()));
    assertEquals(1, result.currentYearEventsApplied().size());
    assertTrue(context.remainingFutureEvents().isEmpty());
  }

  @Test
  void redeemsCurrentYearContractualAssetOnceAndUsesExplicitRedemptionValue() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    ProjectedLongTermAsset deposit =
        contractual(2026, new BigDecimal("120"), InterestTreatment.PAY_OUT);
    CurrentYearBridgeResult result =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock)
                .create(contractualProfile(deposit), assumptionsWithRetirement()));

    BigDecimal fraction =
        new BigDecimal("139").divide(new BigDecimal("365"), 12, java.math.RoundingMode.HALF_UP);
    BigDecimal payout =
        new BigDecimal("100")
            .multiply(new BigDecimal("0.10"))
            .multiply(new BigDecimal("0.80"))
            .multiply(fraction);
    assertEquals(
        0,
        new BigDecimal("120").add(payout).compareTo(cash(result.bridgedProfile())),
        () ->
            "cash="
                + cash(result.bridgedProfile())
                + ", fraction="
                + result.fractionApplied()
                + ", contractual="
                + result.contractualIncomeApplied()
                + ", passive="
                + result.passiveIncomeUsed());
    assertEquals(
        BigDecimal.ZERO, result.bridgedProfile().longTermAssets().getFirst().currentValue());
    assertEquals(new BigDecimal("120"), result.redemptionCashApplied());
    assertEquals(0, payout.compareTo(result.contractualIncomeApplied()));
  }

  @Test
  void leavesFutureMaturityAndDoesNotReplayPastMaturity() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());
    ProjectedLongTermAsset future = contractual(2027, null, InterestTreatment.CAPITALIZE);
    ProjectedLongTermAsset past =
        contractualDate(java.time.LocalDate.of(2026, 6, 1), null, InterestTreatment.CAPITALIZE);
    CurrentYearBridgeResult futureResult =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock)
                .create(contractualProfile(future), assumptionsWithRetirement()));
    CurrentYearBridgeResult pastResult =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock)
                .create(contractualProfile(past), assumptionsWithRetirement()));

    assertTrue(
        futureResult
                .bridgedProfile()
                .longTermAssets()
                .getFirst()
                .currentValue()
                .compareTo(new BigDecimal("100"))
            > 0);
    assertEquals(new BigDecimal("100"), pastResult.redemptionCashApplied());
    assertEquals(
        BigDecimal.ZERO, pastResult.bridgedProfile().longTermAssets().getFirst().currentValue());
  }

  @Test
  void reinvestsUnusedCurrentYearBondMaturityOnceAtEndOfLadder() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    ProjectedLongTermAsset bond =
        new ProjectedLongTermAsset(
            11L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("100"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2026, 1, 1),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)),
            LocalDate.of(2026, 12, 31),
            new BigDecimal("100"),
            InterestTreatment.CAPITALIZE,
            BigDecimal.ZERO);

    InvestmentProfile bridged =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService())
            .projectCurrentYearEnd(
                new ForwardSimulationContextFactory(clock)
                    .create(contractualProfile(bond), assumptionsWithRetirement()))
            .bridgedProfile();

    assertEquals(2, bridged.longTermAssets().size());
    assertEquals(BigDecimal.ZERO, bridged.longTermAssets().getFirst().currentValue());
    ProjectedLongTermAsset reinvested = bridged.longTermAssets().get(1);
    assertEquals(0, new BigDecimal("100").compareTo(reinvested.currentValue()));
    assertEquals(LocalDate.of(2029, 12, 31), reinvested.maturityDate());
    assertEquals(InterestTreatment.CAPITALIZE, reinvested.interestTreatment());
  }

  private static SimulationAssumptions assumptionsWithRetirement() {
    return new SimulationAssumptions(
            40,
            42,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026)
        .withRetirementAge(40);
  }

  private static ProjectedLongTermAsset contractual(
      int maturityYear, BigDecimal redemption, InterestTreatment treatment) {
    return contractualDate(java.time.LocalDate.of(maturityYear, 12, 31), redemption, treatment);
  }

  private static ProjectedLongTermAsset contractualDate(
      java.time.LocalDate maturity, BigDecimal redemption, InterestTreatment treatment) {
    return new ProjectedLongTermAsset(
        10L,
        "Deposit",
        LongTermAssetType.DEPOSIT,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.PLN,
        new BigDecimal("100"),
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                java.time.LocalDate.of(2026, 1, 1),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"))),
        maturity,
        redemption,
        treatment,
        new BigDecimal("0.20"));
  }

  private static InvestmentProfile contractualProfile(ProjectedLongTermAsset asset) {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("100"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                Liquidity.LIQUID)),
        List.of(asset));
  }

  private static BigDecimal cash(InvestmentProfile profile) {
    return profile.allocations().stream()
        .filter(allocation -> allocation.bucket() == EconomicBucket.LIQUID_CASH)
        .map(ProfileAllocation::value)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        com.smartbox.investory.shared.currency.CurrencyType.USD,
        new BigDecimal("1100"),
        BigDecimal.ZERO,
        new BigDecimal("1100"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1100"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, new BigDecimal("1000"), BigDecimal.ZERO, Liquidity.LIQUID)),
        List.of());
  }
}
