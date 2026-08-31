package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Approved failing contract: all funding buckets exhaust in order and the shortfall is preserved.
 */
@DisplayName("Retirement Failure Golden Scenario Integration")
class RetirementFailureGoldenScenarioIntegrationTest {

  @DisplayName("failing Plan Exhausts All Buckets And Reports First And Total Shortfall")
  @Test
  void failingPlanExhaustsAllBucketsAndReportsFirstAndTotalShortfall() {
    var service = new RetirementSimulationService();
    var profile = profile();
    var assumptions =
        SimulationAssumptions.defaults(profile, 65, 66, 2026)
            .withRecurringSpending(bd("500"))
            .withInflationRate(BigDecimal.ZERO)
            .withSpendingGrowthSpread(BigDecimal.ZERO)
            .withRentalIncomeGrowthSpread(BigDecimal.ZERO)
            .withFixedIncomeReturnRate(BigDecimal.ZERO)
            .withEquityReturnRate(BigDecimal.ZERO)
            .withRetirementAge(65);

    var result = service.simulate(profile, assumptions, SimulationScenario.BASE);

    assertThat(result.simulationFailed()).isTrue();
    assertThat(result.failureAge()).isEqualTo(65);
    assertThat(result.firstFailureShortfall()).isEqualByComparingTo("100");
    assertThat(result.totalUnfundedAmount()).isEqualByComparingTo("600");

    var first = result.years().getFirst();
    assertThat(first.unfundedAmount()).isEqualByComparingTo("100");
    assertThat(first.cashEnd()).isZero();
    assertThat(first.fixedIncomeEnd()).isZero();
    assertThat(first.equityEnd()).isZero();
    assertThat(first.realEstateEnd()).isZero();
    assertThat(first.funding().reserveWithdrawal()).isEqualByComparingTo("100");
    assertThat(first.funding().longTermFunding()).isEqualByComparingTo("100");
    assertThat(first.funding().investmentWithdrawal()).isEqualByComparingTo("100");
    assertThat(first.actualPortfolioWithdrawal()).isEqualByComparingTo("400");

    var second = result.years().get(1);
    assertThat(second.year()).isEqualTo(2027);
    assertThat(second.unfundedAmount()).isEqualByComparingTo("500");
    assertThat(second.cashStart()).isZero();
    assertThat(second.fixedIncomeStart()).isZero();
    assertThat(second.equityStart()).isZero();
    assertThat(second.realEstateStart()).isZero();
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        bd("200"),
        bd("200"),
        bd("400"),
        bd("100"),
        bd("100"),
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
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.EQUITY,
                bd("100"),
                BigDecimal.ZERO,
                Liquidity.LIQUID,
                Liquidity.LIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                bd("100"),
                BigDecimal.ZERO,
                Liquidity.ILLIQUID,
                Liquidity.ILLIQUID == com.smartbox.investory.profile.api.model.Liquidity.ILLIQUID
                    ? com.smartbox.investory.profile.api.model.AssetHorizon.LONG_TERM
                    : com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM)),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")),
        bd("200")
            .subtract((bd("100") == null ? java.math.BigDecimal.ZERO : bd("100")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO, bd("200"), BigDecimal.ZERO, bd("200"), BigDecimal.ZERO, bd("400")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
