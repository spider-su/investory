package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProfileAllocation;
import com.smartbox.investory.retirement.simulation.BucketType;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.PlanningBuckets;
import com.smartbox.investory.retirement.simulation.RetirementBucketEngine;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.RetirementSimulationService;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.SimulationYear;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentYearProjectionBridgeTest {
  @Test
  void januaryFirstProjectsFromJanuarySecond() {
    var bridge = bridge("2026-01-01T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo("0.997260273973");
  }

  @Test
  void midYearMatchesCanonicalPeriodStartingTomorrow() {
    var bridge = bridge("2026-07-01T00:00:00Z");
    var expected =
        SimulationPeriod.of(
                java.time.LocalDate.of(2026, 7, 2), java.time.LocalDate.of(2026, 12, 31))
            .yearFraction();

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(expected);
  }

  @Test
  void decemberThirtyFirstHasNoRemainingProjection() {
    var bridge = bridge("2026-12-31T00:00:00Z");

    assertThat(bridge.remainingYearFraction(2026)).isEqualByComparingTo(BigDecimal.ZERO);
  }

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
            result(BucketType.CASH, "100", "80"),
            result(BucketType.BONDS, "200", "210"),
            result(BucketType.EQUITIES, "300", "320"),
            result(BucketType.REAL_ESTATE, "400", "390"),
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

    for (BucketType bucket : BucketType.values()) {
      assertThat(result.expectedEnd(bucket))
          .isEqualByComparingTo(firstProjected.asMap().get(bucket).startValue());
    }
    assertThat(result.bridgedProfile().liquidAssets()).isEqualByComparingTo("110");
    assertThat(result.expectedEnd(BucketType.CASH)).isLessThan(result.start(BucketType.CASH));
  }

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
    assertThat(result.expectedEnd(BucketType.CASH)).isEqualByComparingTo("25");
    assertThat(result.expectedEnd(BucketType.BONDS)).isEqualByComparingTo("100");
  }

  private static CurrentYearProjectionBridge bridge(String instant) {
    return new CurrentYearProjectionBridge(
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), mock(RetirementSimulation.class));
  }

  private static RetirementBucketEngine.BucketResult result(
      BucketType bucket, String start, String end) {
    return new RetirementBucketEngine.BucketResult(
        bucket, bd(start), BigDecimal.ZERO, BigDecimal.ZERO, bd(start).subtract(bd(end)), bd(end));
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        bd("600"),
        bd("400"),
        bd("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        bd("100"),
        bd("400"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH, bd("100"), BigDecimal.ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME, bd("200"), BigDecimal.ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, bd("300"), BigDecimal.ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE, bd("400"), BigDecimal.ZERO, Liquidity.ILLIQUID)),
        List.of());
  }

  private static InvestmentProfile waterfallProfile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        bd("100"),
        bd("100"),
        bd("200"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        bd("100"),
        BigDecimal.ZERO,
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH, bd("100"), BigDecimal.ZERO, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME, bd("100"), BigDecimal.ZERO, Liquidity.LIQUID)),
        List.of());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
