package com.smartbox.investory.retirement.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningBucketsTest {
  @Test
  void keepsAllocationBondWhenFrozenStateOnlyContainsRealEstate() {
    var buckets =
        PlanningBuckets.fromLiveProfileWithBondYield(profileWithMissingFrozenBonds(), ZERO, ZERO);

    assertThat(buckets.cash().startValue()).isEqualByComparingTo("10000");
    assertThat(buckets.bonds().startValue()).isEqualByComparingTo("800000");
    assertThat(buckets.equities().startValue()).isEqualByComparingTo("2000000");
    assertThat(buckets.realEstate().startValue()).isEqualByComparingTo("500000");
  }

  @Test
  void frozenBondValueRemainsAuthoritativeWhenPresent() {
    var profile = profileWithMissingFrozenBonds();
    var bond = asset("Bond", EconomicBucket.FIXED_INCOME, "750000");
    profile =
        withState(profile, List.of(bond, asset("Home", EconomicBucket.REAL_ESTATE, "500000")));

    var buckets = PlanningBuckets.fromReviewedProfileWithBondYield(profile, ZERO, ZERO);

    assertThat(buckets.bonds().startValue()).isEqualByComparingTo("750000");
  }

  @Test
  void reviewedProfileKeepsAllocationBondWhenFrozenStateOnlyContainsRealEstate() {
    var buckets =
        PlanningBuckets.fromReviewedProfileWithBondYield(
            profileWithMissingFrozenBonds(), ZERO, ZERO);

    assertThat(buckets.bonds().startValue()).isEqualByComparingTo("800000");
    assertThat(buckets.realEstate().startValue()).isEqualByComparingTo("500000");
  }

  @Test
  void usesNormalizedLongTermValuesWithoutApplyingAnotherFxConversion() {
    var profile =
        withState(
            profileWithMissingFrozenBonds(),
            List.of(
                asset("Bond", EconomicBucket.FIXED_INCOME, "800000"),
                asset("Home", EconomicBucket.REAL_ESTATE, "3640000")));

    var buckets = PlanningBuckets.fromLiveProfileWithBondYield(profile, ZERO, ZERO);

    assertThat(buckets.bonds().startValue()).isEqualByComparingTo("800000");
    assertThat(buckets.realEstate().startValue()).isEqualByComparingTo("3640000");
  }

  @Test
  void legitimateZeroBondProfileRemainsZero() {
    var profile =
        withState(
            profileWithMissingFrozenBonds(),
            List.of(asset("Home", EconomicBucket.REAL_ESTATE, "500000")));
    profile =
        new InvestmentProfile(
            profile.portfolioId(),
            profile.currency(),
            profile.marketPortfolioValue(),
            profile.longTermAssetValue(),
            profile.totalNetWorth(),
            profile.liquidAssets(),
            profile.illiquidAssets(),
            profile.allocations().stream()
                .filter(a -> a.bucket() != EconomicBucket.FIXED_INCOME)
                .toList(),
            profile.currentRentalIncome(),
            profile.currentBondIncome(),
            profile.longTermPlanningState(),
            profile.retirementReserve(),
            profile.investmentCapital(),
            profile.incomeSummary(),
            profile.allocationReconciliation());

    assertThat(
            PlanningBuckets.fromLiveProfileWithBondYield(profile, ZERO, ZERO).bonds().startValue())
        .isZero();
  }

  private static InvestmentProfile profileWithMissingFrozenBonds() {
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
        bd("31800"),
        bd("31800"),
        new ProfileAssetProjection(
            List.of(asset("Home", EconomicBucket.REAL_ESTATE, "500000")),
            ZERO,
            2026,
            ProjectionSource.ACTUAL),
        bd("10000"),
        bd("2000000"),
        new ProfileIncomeSummary(null, null, null, bd("31800"), null, null, null),
        ProfileAllocationReconciliation.EMPTY);
  }

  private static InvestmentProfile withState(
      InvestmentProfile profile, List<ProjectedLongTermAsset> assets) {
    return new InvestmentProfile(
        profile.portfolioId(),
        profile.currency(),
        profile.marketPortfolioValue(),
        profile.longTermAssetValue(),
        profile.totalNetWorth(),
        profile.liquidAssets(),
        profile.illiquidAssets(),
        profile.allocations(),
        profile.currentRentalIncome(),
        profile.currentBondIncome(),
        new ProfileAssetProjection(assets, ZERO, 2026, ProjectionSource.ACTUAL),
        profile.retirementReserve(),
        profile.investmentCapital(),
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }

  private static ProfileAllocation allocation(EconomicBucket bucket, String value) {
    return new ProfileAllocation(
        bucket,
        bd(value),
        ZERO,
        Liquidity.LIQUID,
        com.smartbox.investory.profile.api.model.AssetHorizon.SHORT_TERM);
  }

  private static ProjectedLongTermAsset asset(String name, EconomicBucket bucket, String value) {
    return new ProjectedLongTermAsset(
        1L,
        name,
        bucket,
        CurrencyType.PLN,
        bd(value),
        bucket == EconomicBucket.REAL_ESTATE ? Liquidity.ILLIQUID : Liquidity.LIQUID,
        List.of(),
        List.of(),
        null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static final BigDecimal ZERO = BigDecimal.ZERO;
}
