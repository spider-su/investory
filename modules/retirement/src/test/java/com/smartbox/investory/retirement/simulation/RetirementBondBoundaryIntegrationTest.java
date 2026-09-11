package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.profile.api.model.*;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.projection.CurrentYearProjectionBridge;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetirementBondBoundaryIntegrationTest {
  @Test
  void keepsBondCashIncomeSeparateWhileApplyingBondReturnToCapital() {
    var cashFlows = mock(FrozenBondCashFlowProjection.class);
    when(cashFlows.cashIncome(any(), any(), anyInt())).thenReturn(bd("31800"));
    var simulation = new RetirementSimulationService(cashFlows);
    var assumptions =
        SimulationAssumptions.defaults(60, 61, 2026)
            .withRecurringSpending(ZERO)
            .withInflationRate(ZERO)
            .withFixedIncomeReturnRate(new BigDecimal("0.04"))
            .withEquityReturnRate(ZERO);

    var year =
        simulation.simulateRemainingYear(
            profileWithRealEstateButMissingFrozenBonds(),
            assumptions,
            SimulationScenario.BASE,
            2026,
            BigDecimal.ONE);

    assertThat(year.bondIncome()).isEqualByComparingTo("31800");
    assertThat(year.cashEnd()).isEqualByComparingTo("10000");
    assertThat(year.fixedIncomeEnd()).isEqualByComparingTo("832000");
  }

  @Test
  void preservesAllocationBondsAcrossCurrentYearBoundaryWhenRealEstateIsFrozen() {
    var clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    var simulation = new RetirementSimulationService();
    var profile = profileWithRealEstateButMissingFrozenBonds();
    var assumptions =
        SimulationAssumptions.defaults(60, 61, 2026)
            .withRetirementAge(60)
            .withRecurringSpending(ZERO)
            .withInflationRate(ZERO)
            .withSpendingGrowthSpread(ZERO)
            .withRentalIncomeGrowthSpread(ZERO)
            .withFixedIncomeReturnRate(new BigDecimal("0.10"))
            .withEquityReturnRate(ZERO);

    var bridge =
        new CurrentYearProjectionBridge(
            clock, simulation, new ForwardSimulationContextFactory(clock));
    var context = new ForwardSimulationContextFactory(clock).create(profile, assumptions);
    var reviewedProfile = profileWithHiddenReviewedBond(profile);
    var current = bridge.projectCurrentYearEnd(context, reviewedProfile);
    var currentBond = current.bucketBoundaries().get(EconomicBucket.FIXED_INCOME);

    assertThat(profile.currentBondIncome()).isEqualByComparingTo("31800");
    assertThat(current.start(EconomicBucket.LIQUID_CASH)).isEqualByComparingTo("10000");
    assertThat(currentBond.startValue()).isEqualByComparingTo("800000");
    assertThat(current.start(EconomicBucket.EQUITY)).isEqualByComparingTo("1200000");
    assertThat(current.start(EconomicBucket.REAL_ESTATE)).isEqualByComparingTo("500000");
    assertThat(currentBond.expectedEndValue()).isGreaterThan(currentBond.startValue());
    assertThat(currentBond.expectedEndValue()).isNotZero();

    var forwardAssumptions = context.forwardAssumptions().orElseThrow();
    var firstProjected =
        simulation
            .simulate(
                current.bridgedProfile(),
                forwardAssumptions,
                SimulationScenario.BASE,
                context.asOfYear())
            .years()
            .getFirst();

    assertThat(firstProjected.cashStart())
        .isEqualByComparingTo(current.expectedEnd(EconomicBucket.LIQUID_CASH));
    assertThat(firstProjected.fixedIncomeStart())
        .isEqualByComparingTo(currentBond.expectedEndValue());
    assertThat(firstProjected.equityStart())
        .isEqualByComparingTo(current.expectedEnd(EconomicBucket.EQUITY));
    assertThat(firstProjected.realEstateStart())
        .isEqualByComparingTo(current.expectedEnd(EconomicBucket.REAL_ESTATE));
    assertThat(firstProjected.fixedIncomeEnd())
        .isEqualByComparingTo(firstProjected.fixedIncomeStart());
  }

  @Test
  void safeReserveYearsChangesTheAssetConsumptionOrder() {
    var cashFlows = mock(FrozenBondCashFlowProjection.class);
    when(cashFlows.cashIncome(any(), any(), anyInt())).thenReturn(ZERO);
    var simulation = new RetirementSimulationService(cashFlows);
    var base =
        SimulationAssumptions.defaults(60, 60, 2026)
            .withRetirementAge(60)
            .withRecurringSpending(bd("500000"))
            .withInflationRate(ZERO)
            .withSpendingGrowthSpread(ZERO)
            .withRentalIncomeGrowthSpread(ZERO)
            .withFixedIncomeReturnRate(ZERO)
            .withEquityReturnRate(ZERO);

    var profile = profileWithRealEstateButMissingFrozenBonds();
    var noReserve =
        simulation
            .simulate(
                profile, base.toBuilder().safeReserveYears(ZERO).build(), SimulationScenario.BASE)
            .years()
            .getFirst();
    var protectedReserve =
        simulation
            .simulate(
                profile,
                base.toBuilder().safeReserveYears(bd("5")).build(),
                SimulationScenario.BASE)
            .years()
            .getFirst();

    assertThat(noReserve.fixedIncomeEnd()).isEqualByComparingTo("310000");
    assertThat(noReserve.equityEnd()).isEqualByComparingTo("1200000");
    assertThat(noReserve.safeReserveTarget()).isZero();
    assertThat(protectedReserve.fixedIncomeEnd()).isEqualByComparingTo("800000");
    assertThat(protectedReserve.equityEnd()).isEqualByComparingTo("710000");
    assertThat(protectedReserve.safeReserveTarget()).isEqualByComparingTo("2500000");
  }

  @Test
  void oneOffExpenseRaisesFundingGapButNotReserveTarget() {
    var cashFlows = mock(FrozenBondCashFlowProjection.class);
    when(cashFlows.cashIncome(any(), any(), anyInt())).thenReturn(bd("170000"));
    var assumptions =
        SimulationAssumptions.defaults(60, 60, 2026)
            .withRetirementAge(60)
            .withRecurringSpending(bd("250000"))
            .withInflationRate(ZERO)
            .withSpendingGrowthSpread(ZERO)
            .withRentalIncomeGrowthSpread(ZERO)
            .withFixedIncomeReturnRate(ZERO)
            .withEquityReturnRate(ZERO)
            .toBuilder()
            .safeReserveYears(bd("5"))
            .futureEvents(
                List.of(
                    new SimulationEvent(
                        1L,
                        2026,
                        "One-off",
                        bd("120000"),
                        SimulationEventType.ONE_OFF_EXPENSE,
                        null)))
            .build();

    var year =
        new RetirementSimulationService(cashFlows)
            .simulate(
                profileWithRealEstateButMissingFrozenBonds(), assumptions, SimulationScenario.BASE)
            .years()
            .getFirst();

    assertThat(year.safeReserveTarget()).isEqualByComparingTo("400000");
    assertThat(year.requiredPortfolioFunding()).isEqualByComparingTo("200000");
    assertThat(year.actualPortfolioWithdrawal()).isEqualByComparingTo("200000");
  }

  @Test
  void liveOneOffExpenseDoesNotRaisePartialYearReserveFloor() {
    var cashFlows = mock(FrozenBondCashFlowProjection.class);
    when(cashFlows.cashIncome(any(), any(), anyInt())).thenReturn(bd("170000"));
    var clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    var simulation = new RetirementSimulationService(cashFlows);
    var assumptions =
        SimulationAssumptions.defaults(60, 61, 2026)
            .withRetirementAge(60)
            .withRecurringSpending(bd("250000"))
            .withInflationRate(ZERO)
            .withSpendingGrowthSpread(ZERO)
            .withRentalIncomeGrowthSpread(ZERO)
            .withFixedIncomeReturnRate(ZERO)
            .withEquityReturnRate(ZERO)
            .toBuilder()
            .safeReserveYears(bd("5"))
            .futureEvents(
                List.of(
                    new SimulationEvent(
                        1L,
                        2026,
                        "One-off",
                        bd("120000"),
                        SimulationEventType.ONE_OFF_EXPENSE,
                        null)))
            .build();

    var context =
        new ForwardSimulationContextFactory(clock)
            .create(profileWithRealEstateButMissingFrozenBonds(), assumptions);
    var current =
        new CurrentYearProjectionBridge(
                clock, simulation, new ForwardSimulationContextFactory(clock))
            .projectCurrentYearEnd(context);
    BigDecimal fraction =
        SimulationPeriod.fraction(
            java.time.LocalDate.of(2026, 7, 2), java.time.LocalDate.of(2026, 12, 31));

    assertThat(current.expectedEnd(EconomicBucket.FIXED_INCOME))
        .isEqualByComparingTo(bd("690000").subtract(bd("80000").multiply(fraction)));
    assertThat(current.expectedEnd(EconomicBucket.FIXED_INCOME)).isLessThan(bd("800000"));
  }

  private static InvestmentProfile profileWithHiddenReviewedBond(InvestmentProfile live) {
    var hiddenBond =
        new ProjectedLongTermAsset(
            2L,
            "Reviewed bond",
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            bd("1100000"),
            Liquidity.LIQUID,
            List.of(),
            List.of(),
            null);
    return new InvestmentProfile(
        live.portfolioId(),
        live.currency(),
        live.marketPortfolioValue(),
        live.longTermAssetValue(),
        live.totalNetWorth(),
        live.liquidAssets(),
        live.illiquidAssets(),
        live.allocations(),
        live.currentRentalIncome(),
        live.currentBondIncome(),
        new ProfileAssetProjection(
            List.of(hiddenBond, live.longTermPlanningState().assets().getFirst()),
            ZERO,
            2026,
            ProjectionSource.ACTUAL),
        live.retirementReserve(),
        live.investmentCapital(),
        live.incomeSummary(),
        live.allocationReconciliation());
  }

  private static InvestmentProfile profileWithRealEstateButMissingFrozenBonds() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        bd("2010000"),
        bd("500000"),
        bd("2510000"),
        bd("10000"),
        bd("500000"),
        List.of(
            allocation(EconomicBucket.LIQUID_CASH, "10000"),
            allocation(EconomicBucket.FIXED_INCOME, "800000"),
            allocation(EconomicBucket.EQUITY, "1200000")),
        ZERO,
        bd("31800"),
        new ProfileAssetProjection(
            List.of(
                new ProjectedLongTermAsset(
                    1L,
                    "Home",
                    EconomicBucket.REAL_ESTATE,
                    CurrencyType.PLN,
                    bd("500000"),
                    Liquidity.ILLIQUID,
                    List.of(),
                    List.of(),
                    null)),
            ZERO,
            2026,
            ProjectionSource.ACTUAL),
        bd("10000"),
        bd("1200000"),
        new ProfileIncomeSummary(null, null, null, bd("31800"), null, null, null),
        ProfileAllocationReconciliation.EMPTY);
  }

  private static ProfileAllocation allocation(EconomicBucket bucket, String value) {
    return new ProfileAllocation(
        bucket, bd(value), ZERO, Liquidity.LIQUID, AssetHorizon.SHORT_TERM);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static final BigDecimal ZERO = BigDecimal.ZERO;
}
