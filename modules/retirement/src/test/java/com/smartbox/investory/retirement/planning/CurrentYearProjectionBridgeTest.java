package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBuckets;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationYear;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.RetirementSimulationService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Current Year Projection Bridge")
class CurrentYearProjectionBridgeTest {
  @DisplayName("january First Projects From January Second")
  @Test
  void januaryFirstProjectsFromJanuarySecond() {
    var bridge = bridge("2026-01-01T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo("0.997260273973");
  }

  @DisplayName("mid Year Matches Canonical Period Starting Tomorrow")
  @Test
  void midYearMatchesCanonicalPeriodStartingTomorrow() {
    var bridge = bridge("2026-07-01T00:00:00Z");
    var expected =
        SimulationPeriod.of(
                java.time.LocalDate.of(2026, 7, 2), java.time.LocalDate.of(2026, 12, 31))
            .yearFraction();

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(expected);
  }

  @DisplayName("december Thirty First Has No Remaining Projection")
  @Test
  void decemberThirtyFirstHasNoRemainingProjection() {
    var bridge = bridge("2026-12-31T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @DisplayName("leap Year Uses Leap Year Denominator")
  @Test
  void leapYearUsesLeapYearDenominator() {
    var bridge = bridge("2024-07-01T00:00:00Z");
    var expected =
        SimulationPeriod.of(
                java.time.LocalDate.of(2024, 7, 2), java.time.LocalDate.of(2024, 12, 31))
            .yearFraction();

    assertThat(bridge.remainingYearFraction(2024)).isEqualByComparingTo(expected);
    assertThat(expected).isEqualByComparingTo("0.5");
  }

  @DisplayName("current Expected Bucket Ends Are The First Projected Starts")
  @Test
  void currentExpectedBucketEndsAreTheFirstProjectedStarts() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 42, 2026);
    SimulationYear currentYear =
        SimulationYear.bucket(
            40,
            2026,
            true,
            bd("100"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            result(EconomicBucket.LIQUID_CASH, "100", "80"),
            result(EconomicBucket.FIXED_INCOME, "200", "210"),
            result(EconomicBucket.EQUITY, "300", "320"),
            result(EconomicBucket.REAL_ESTATE, "400", "390"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    when(simulations.simulateRemainingYear(
            eq(profile),
            eq(assumptions),
            eq(SimulationScenario.BASE),
            eq(2026),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(currentYear);

    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(
            clock, simulations, new ForwardSimulationContextFactory(clock));
    var context = new ForwardSimulationContextFactory(clock).create(profile, assumptions);
    var result = bridge.projectCurrentYearEnd(context);
    var firstProjected =
        PlanningBuckets.fromProfileWithBondYield(
            result.bridgedProfile(), assumptions.equityReturnRate(), BigDecimal.ZERO);

    for (EconomicBucket bucket :
        List.of(
            EconomicBucket.LIQUID_CASH,
            EconomicBucket.FIXED_INCOME,
            EconomicBucket.EQUITY,
            EconomicBucket.REAL_ESTATE)) {
      assertThat(result.expectedEnd(bucket))
          .isEqualByComparingTo(firstProjected.asMap().get(bucket).startValue());
    }
    assertThat(result.bridgedProfile().liquidAssets()).isEqualByComparingTo("110");
    assertThat(result.expectedEnd(EconomicBucket.LIQUID_CASH))
        .isLessThan(result.start(EconomicBucket.LIQUID_CASH));
  }

  @DisplayName("capitalized frozen bonds follow the bridged bucket")
  @Test
  void capitalizedFrozenBondsFollowBridgedBucket() {
    assertFrozenAssetsAreRebased(InterestTreatment.CAPITALIZE);
  }

  @DisplayName("pay out frozen bonds follow the bridged bucket")
  @Test
  void payOutFrozenBondsFollowBridgedBucket() {
    assertFrozenAssetsAreRebased(InterestTreatment.PAY_OUT);
  }

  @DisplayName("frozen real estate follows the bridged bucket")
  @Test
  void frozenRealEstateFollowsBridgedBucket() {
    var result = bridgedFrozenProfile(InterestTreatment.PAY_OUT);
    var state = result.bridgedProfile().longTermPlanningState();

    assertThat(state.assets())
        .filteredOn(asset -> asset.type() == LongTermAssetType.REAL_ESTATE)
        .singleElement()
        .extracting(ProjectedLongTermAsset::currentValue)
        .satisfies(value -> assertThat(value).isEqualByComparingTo("390"));
    assertThat(
            PlanningBuckets.fromProfileWithBondYield(
                    result.bridgedProfile(), BigDecimal.ZERO, BigDecimal.ZERO)
                .realEstate()
                .startValue())
        .isEqualByComparingTo("390");
  }

  private static void assertFrozenAssetsAreRebased(InterestTreatment treatment) {
    var result = bridgedFrozenProfile(treatment);
    var state = result.bridgedProfile().longTermPlanningState();

    assertThat(state.assets())
        .filteredOn(asset -> asset.type() == LongTermAssetType.BOND)
        .singleElement()
        .satisfies(
            asset -> {
              assertThat(asset.currentValue()).isEqualByComparingTo("210");
              assertThat(asset.interestTreatment()).isEqualTo(treatment);
            });
    assertThat(
            PlanningBuckets.fromProfileWithBondYield(
                    result.bridgedProfile(), BigDecimal.ZERO, BigDecimal.ZERO)
                .bonds()
                .startValue())
        .isEqualByComparingTo("210");
  }

  private static CurrentYearBridgeResult bridgedFrozenProfile(InterestTreatment treatment) {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    InvestmentProfile profile = frozenProfile(treatment);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 42, 2026);
    SimulationYear currentYear =
        SimulationYear.bucket(
            40,
            2026,
            true,
            bd("100"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            bd("0"),
            result(EconomicBucket.LIQUID_CASH, "100", "80"),
            result(EconomicBucket.FIXED_INCOME, "200", "210"),
            result(EconomicBucket.EQUITY, "300", "320"),
            result(EconomicBucket.REAL_ESTATE, "400", "390"),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    when(simulations.simulateRemainingYear(
            eq(profile),
            eq(assumptions),
            eq(SimulationScenario.BASE),
            eq(2026),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(currentYear);
    return new CurrentYearProjectionBridge(
            clock, simulations, new ForwardSimulationContextFactory(clock))
        .projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock).create(profile, assumptions));
  }

  @DisplayName("remaining Year Runs The Cash First Waterfall Instead Of Interpolating Every Bucket")
  @Test
  void remainingYearRunsTheCashFirstWaterfallInsteadOfInterpolatingEveryBucket() {
    Clock clock = Clock.fixed(Instant.parse("2024-07-01T00:00:00Z"), ZoneOffset.UTC);
    InvestmentProfile profile = waterfallProfile();
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 65, 66, 2024)
            .withRecurringSpending(bd("150"))
            .withFixedIncomeReturnRate(BigDecimal.ZERO)
            .withEquityReturnRate(BigDecimal.ZERO);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(clock, new RetirementSimulationService());

    var result =
        bridge.projectCurrentYearEnd(
            new ForwardSimulationContextFactory(clock).create(profile, assumptions));

    assertThat(result.fractionApplied()).isEqualByComparingTo("0.5");
    assertThat(result.expectedEnd(EconomicBucket.LIQUID_CASH)).isEqualByComparingTo("25");
    assertThat(result.expectedEnd(EconomicBucket.FIXED_INCOME)).isEqualByComparingTo("100");
  }

  private static CurrentYearProjectionBridge bridge(String instant) {
    return new CurrentYearProjectionBridge(
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), mock(RetirementSimulation.class));
  }

  private static BucketResult result(EconomicBucket bucket, String start, String end) {
    return new BucketResult(
        bucket, bd(start), BigDecimal.ZERO, BigDecimal.ZERO, bd(start).subtract(bd(end)), bd(end));
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        bd("600"),
        bd("400"),
        bd("1000"),
        bd("100"),
        bd("400"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                bd("100"),
                BigDecimal.ZERO,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                bd("200"),
                BigDecimal.ZERO,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.EQUITY,
                bd("300"),
                BigDecimal.ZERO,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                bd("400"),
                BigDecimal.ZERO,
                Liquidity.ILLIQUID,
                Liquidity.ILLIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")),
        bd("600")
            .subtract((bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO, bd("600"), BigDecimal.ZERO, bd("400"), BigDecimal.ZERO, bd("1000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile frozenProfile(InterestTreatment treatment) {
    var bond =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            bd("200"),
            Liquidity.LIQUID,
            List.of(),
            List.of(),
            null,
            null,
            treatment,
            BigDecimal.ZERO,
            null,
            false);
    var realEstate =
        new ProjectedLongTermAsset(
            2L,
            "Property",
            LongTermAssetType.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.USD,
            bd("400"),
            Liquidity.ILLIQUID,
            List.of(),
            List.of(),
            null,
            null,
            null,
            BigDecimal.ZERO,
            null,
            false);
    var base = profile();
    return new InvestmentProfile(
        base.portfolioId(),
        base.currency(),
        base.marketPortfolioValue(),
        base.longTermAssetValue(),
        base.totalNetWorth(),
        base.liquidAssets(),
        base.illiquidAssets(),
        base.allocations(),
        base.currentRentalIncome(),
        base.currentBondIncome(),
        new ProfileAssetProjection(
            List.of(bond, realEstate),
            BigDecimal.ZERO,
            2026,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        base.retirementReserve(),
        base.investmentCapital(),
        base.incomeSummary(),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile waterfallProfile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        bd("100"),
        bd("100"),
        bd("200"),
        bd("100"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                bd("100"),
                BigDecimal.ZERO,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                bd("100"),
                BigDecimal.ZERO,
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
        (bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")),
        bd("100")
            .subtract((bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, bd("100"), BigDecimal.ZERO, bd("200")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
