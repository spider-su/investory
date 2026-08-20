package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProfileAllocation;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ReserveAndHarvestStrategyTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final RetirementSimulationService service = new RetirementSimulationService();

  @Test
  @Disabled
  void reserveTargetUsesRecurringFundingGapAndHarvestFillsOnlyItsShortfall() {
    Map<EconomicBucket, BigDecimal> starting =
        Map.of(
            EconomicBucket.LIQUID_CASH,
            new BigDecimal("25"),
            EconomicBucket.FIXED_INCOME,
            new BigDecimal("95"),
            EconomicBucket.EQUITY,
            new BigDecimal("620"));
    SimulationYear year =
        simulate(
            starting,
            assumptions(
                new BigDecimal("25"), new BigDecimal("0.08"), new BigDecimal("0.75"), true));
    assertBd("25", year.recurringFundingGap());
    assertBd("125", year.safeReserveTarget());
    assertBd("49.6", year.equityGain());
    assertBd("30", year.equityToFixedIncomeTransfer());
    assertBd("125", year.safeReserveEnd());
    assertBd("639.6", year.equityEnd());
    assertBd("0", year.unfundedAmount());
    assertBd("764.6", year.endNetWorth());
    SimulationYear waterfall =
        simulate(
            starting, simpleWaterfallAssumptions(new BigDecimal("25"), new BigDecimal("0.08")));
    assertBd("0", waterfall.equityToFixedIncomeTransfer());
    assertBd("95", waterfall.safeReserveEnd());
    assertBd("669.6", waterfall.equityEnd());
    assertBd("764.6", waterfall.endNetWorth());
  }

  @Test
  void harvestRespectsTransferRateThresholdAndPositiveGainOnly() {
    SimulationYear half =
        simulate(
            Map.of(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("25"),
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("85"),
                EconomicBucket.EQUITY,
                new BigDecimal("625")),
            assumptions(
                new BigDecimal("25"), new BigDecimal("0.08"), new BigDecimal("0.50"), true));
    assertBd("25", half.equityToFixedIncomeTransfer());
    SimulationYear belowThreshold =
        simulate(
            Map.of(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("25"),
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("90"),
                EconomicBucket.EQUITY,
                new BigDecimal("620")),
            assumptions(new BigDecimal("25"), new BigDecimal("0.069"), BigDecimal.ONE, true));
    assertBd("0", belowThreshold.equityToFixedIncomeTransfer());
    SimulationYear atThreshold =
        simulate(
            Map.of(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("25"),
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("90"),
                EconomicBucket.EQUITY,
                new BigDecimal("620")),
            assumptions(new BigDecimal("25"), new BigDecimal("0.07"), BigDecimal.ONE, true));
    assertBd("35", atThreshold.equityToFixedIncomeTransfer());
    SimulationYear negative =
        simulate(
            Map.of(
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("90"),
                EconomicBucket.EQUITY,
                new BigDecimal("620")),
            assumptions(ZERO, new BigDecimal("-0.08"), BigDecimal.ONE, true));
    assertBd("0", negative.equityGain());
    assertBd("0", negative.equityToFixedIncomeTransfer());
  }

  @Test
  void emergencyEquityIsExplicitAndReturnAppliesOnlyToBalanceLeftAfterSpending() {
    SimulationYear emergencyOff =
        simulate(
            Map.of(EconomicBucket.EQUITY, new BigDecimal("100")),
            assumptions(new BigDecimal("50"), new BigDecimal("0.08"), BigDecimal.ZERO, false));
    assertBd("0", emergencyOff.actualPortfolioWithdrawal());
    assertBd("50", emergencyOff.unfundedAmount());
    assertBd("0", emergencyOff.emergencyEquityWithdrawal());
    SimulationYear emergencyOn =
        simulate(
            Map.of(EconomicBucket.EQUITY, new BigDecimal("100")),
            assumptions(new BigDecimal("50"), new BigDecimal("0.08"), BigDecimal.ZERO, true));
    assertBd("50", emergencyOn.actualPortfolioWithdrawal());
    assertBd("0", emergencyOn.unfundedAmount());
    assertBd("50", emergencyOn.emergencyEquityWithdrawal());
    assertBd("54", emergencyOn.equityEnd());
  }

  @Test
  void simpleWaterfallUsesEquityForFundingButReserveStrategyDoesNotWithoutEmergencyPermission() {
    InvestmentProfile profile = profile(Map.of(EconomicBucket.EQUITY, new BigDecimal("100")));
    SimulationAssumptions reserve = assumptions(new BigDecimal("50"), ZERO, ZERO, false);
    SimulationAssumptions waterfall =
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
            SimulationFundingStrategy.SIMPLE_WATERFALL,
            ZERO,
            ZERO,
            ZERO,
            true);
    assertBd(
        "0",
        service
            .simulate(profile, reserve, SimulationScenario.BASE)
            .finalYear()
            .actualPortfolioWithdrawal());
    assertBd(
        "50",
        service
            .simulate(profile, waterfall, SimulationScenario.BASE)
            .finalYear()
            .actualPortfolioWithdrawal());
  }

  @Test
  void manualCashReserveFundsBeforeMarketCashAndFixedIncomeAndEarnsItsOwnRate() {
    ProjectedLongTermAsset reserve =
        new ProjectedLongTermAsset(
            10L,
            "Reserve",
            LongTermAssetType.CASH_RESERVE,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("40"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    java.time.LocalDate.of(2026, 1, 1), null, ZERO, ZERO, new BigDecimal("0.10"))),
            null,
            null,
            null,
            ZERO);
    InvestmentProfile first =
        manualReserveProfile(
            new BigDecimal("10"), new BigDecimal("150"), new BigDecimal("620"), reserve);
    SimulationYear fundedFromReserve =
        service
            .simulate(
                first,
                assumptions(new BigDecimal("25"), ZERO, ZERO, false),
                SimulationScenario.BASE)
            .finalYear();
    assertBd("40", fundedFromReserve.manualLiquidReserveStart());
    assertBd("25", fundedFromReserve.manualLiquidReserveWithdrawal());
    assertBd("16.5", fundedFromReserve.manualLiquidReserveEnd());
    assertBd("10", fundedFromReserve.cashEnd());
    assertBd("150", fundedFromReserve.fixedIncomeEnd());
    assertBd("620", fundedFromReserve.equityEnd());

    ProjectedLongTermAsset smaller =
        new ProjectedLongTermAsset(
            10L,
            "Reserve",
            LongTermAssetType.CASH_RESERVE,
            EconomicBucket.LIQUID_CASH,
            CurrencyType.PLN,
            new BigDecimal("20"),
            Liquidity.LIQUID,
            List.of(),
            null,
            null,
            null,
            ZERO);
    SimulationYear cascade =
        service
            .simulate(
                manualReserveProfile(
                    new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("620"), smaller),
                assumptions(new BigDecimal("50"), ZERO, ZERO, false),
                SimulationScenario.BASE)
            .finalYear();
    assertBd("20", cascade.manualLiquidReserveWithdrawal());
    assertBd("0", cascade.cashEnd());
    assertBd("75", cascade.fixedIncomeEnd());
    assertBd("620", cascade.equityEnd());
  }

  private SimulationYear simulate(
      Map<EconomicBucket, BigDecimal> values, SimulationAssumptions assumptions) {
    return service.simulate(profile(values), assumptions, SimulationScenario.BASE).finalYear();
  }

  private static SimulationAssumptions assumptions(
      BigDecimal spending, BigDecimal equityReturn, BigDecimal transferRate, boolean emergency) {
    return new SimulationAssumptions(
        40,
        40,
        spending,
        ZERO,
        ZERO,
        ZERO,
        equityReturn,
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
        transferRate,
        emergency);
  }

  private static SimulationAssumptions simpleWaterfallAssumptions(
      BigDecimal spending, BigDecimal equityReturn) {
    return new SimulationAssumptions(
        40,
        40,
        spending,
        ZERO,
        ZERO,
        ZERO,
        equityReturn,
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
        SimulationFundingStrategy.SIMPLE_WATERFALL,
        ZERO,
        ZERO,
        ZERO,
        true);
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
            .filter(entry -> entry.getKey() != EconomicBucket.REAL_ESTATE)
            .map(Map.Entry::getValue)
            .reduce(ZERO, BigDecimal::add),
        values.getOrDefault(EconomicBucket.REAL_ESTATE, ZERO),
        values.entrySet().stream()
            .map(
                entry ->
                    new ProfileAllocation(
                        entry.getKey(),
                        entry.getValue(),
                        BigDecimal.ONE,
                        entry.getKey() == EconomicBucket.REAL_ESTATE
                            ? Liquidity.ILLIQUID
                            : Liquidity.LIQUID))
            .toList(),
        List.of());
  }

  private static InvestmentProfile manualReserveProfile(
      BigDecimal marketCash,
      BigDecimal fixedIncome,
      BigDecimal equity,
      ProjectedLongTermAsset reserve) {
    BigDecimal total = marketCash.add(fixedIncome).add(equity).add(reserve.currentValue());
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        total,
        ZERO,
        total,
        ZERO,
        ZERO,
        ZERO,
        marketCash.add(fixedIncome).add(equity),
        reserve.currentValue(),
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                marketCash.add(reserve.currentValue()),
                BigDecimal.ZERO,
                Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME, fixedIncome, BigDecimal.ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, equity, BigDecimal.ZERO, Liquidity.LIQUID)),
        List.of(reserve));
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
