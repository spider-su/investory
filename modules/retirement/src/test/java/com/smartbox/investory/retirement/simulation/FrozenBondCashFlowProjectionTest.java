package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrozenBondCashFlowProjectionTest {
  private final FrozenBondCashFlowProjection projection = new FrozenBondCashFlowProjection();

  @Test
  void payOutUsesNormalizedNetIncomeAndStopsAfterMaturity() {
    var asset =
        asset(
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("80"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            LocalDate.of(2028, 12, 31));

    assertThat(projection.cashIncome(profile(asset), assumptions(), 2028))
        .isEqualByComparingTo("80");
    assertThat(projection.cashIncome(profile(asset), assumptions(), 2029)).isZero();
  }

  @Test
  void capitalizeIncomeIsCapitalReturnAndNotCashIncome() {
    var asset =
        asset(
            InterestTreatmentModel.CAPITALIZE,
            new BigDecimal("80"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            null);
    var profile = profile(asset);

    assertThat(projection.cashIncome(profile, assumptions(), 2026)).isZero();
    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2026))
        .isEqualByComparingTo("0.08");
    assertThat(projection.hasCapitalizedBondYield(profile, 2026)).isTrue();
  }

  @Test
  void payOutOnlySourceDoesNotFallBackToCapitalizedPlanningYield() {
    var asset =
        asset(
            InterestTreatmentModel.PAY_OUT,
            null,
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            null);
    var profile = profile(asset);

    assertThat(projection.hasFrozenBondAssets(profile)).isTrue();
    assertThat(projection.hasCapitalizedBondYield(profile, 2026)).isFalse();
    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2026)).isZero();
  }

  @Test
  void capitalizedYieldUsesPeriodActiveAtBaselineYear() {
    var profile =
        profile(
            assetWithPeriods(
                InterestTreatmentModel.CAPITALIZE,
                period("2024-01-01", "2026-12-31", "30", "0.03"),
                period("2027-01-01", "2030-12-31", "50", "0.05")));

    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2027))
        .isEqualByComparingTo("0.05");
    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2026))
        .isEqualByComparingTo("0.03");
  }

  @Test
  void capitalizedYieldDoesNotDependOnPeriodListOrder() {
    var profile =
        profile(
            assetWithPeriods(
                InterestTreatmentModel.CAPITALIZE,
                period("2027-01-01", "2030-12-31", "50", "0.05"),
                period("2024-01-01", "2026-12-31", "30", "0.03")));

    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2027))
        .isEqualByComparingTo("0.05");
  }

  @Test
  void capitalizedYieldHasNoArbitraryFallbackWhenBaselineHasNoActivePeriod() {
    var profile =
        profile(
            assetWithPeriods(
                InterestTreatmentModel.CAPITALIZE,
                period("2024-01-01", "2026-12-31", null, "0.03")));

    assertThat(projection.hasCapitalizedBondYield(profile, 2027)).isFalse();
    assertThat(projection.baseCapitalizedBondYield(profile, new BigDecimal("0.04"), 2027)).isZero();
  }

  @Test
  void planBondReturnExposureMatchesSourceTreatmentAndForwardActivity() {
    var payout =
        assetWithPeriods(InterestTreatmentModel.PAY_OUT, period("2026-01-01", null, "80", "0.04"));
    var activeCap =
        assetWithPeriods(
            InterestTreatmentModel.CAPITALIZE, period("2026-01-01", "2028-12-31", "80", "0.04"));
    var expiredCap =
        assetWithPeriods(
            InterestTreatmentModel.CAPITALIZE, period("2020-01-01", "2025-12-31", "80", "0.04"));

    assertThat(projection.hasPlanBondReturnExposure(profile(payout), 2026, 2028)).isFalse();
    assertThat(projection.hasPlanBondReturnExposure(profile(activeCap), 2026, 2028)).isTrue();
    assertThat(projection.hasPlanBondReturnExposure(profile(expiredCap), 2026, 2028)).isFalse();
    assertThat(
            projection.hasPlanBondReturnExposure(profile(List.of(payout, activeCap)), 2026, 2028))
        .isTrue();
  }

  @Test
  void allocationOnlyFixedIncomeHasPlanReturnExposure() {
    var profile =
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
                new com.smartbox.investory.retirement.profile.ProfileAllocation(
                    EconomicBucket.FIXED_INCOME,
                    new BigDecimal("1000"),
                    BigDecimal.ONE,
                    Liquidity.LIQUID)),
            List.of());

    assertThat(projection.hasPlanBondReturnExposure(profile, 2026, 2028)).isTrue();
  }

  @Test
  void explicitAnnualIncomeIsAlreadyNetAndIsNotTaxedAgain() {
    var asset =
        asset(
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("80"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            null);

    assertThat(projection.cashIncome(profile(asset), assumptions(), 2026))
        .isEqualByComparingTo("80");
  }

  @Test
  void reviewedSnapshotIgnoresLaterLiveBondEdits() {
    var reviewed =
        asset(
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("80"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            null);
    var edited =
        new ProjectedLongTermAsset(
            reviewed.id(),
            reviewed.name(),
            reviewed.type(),
            reviewed.bucket(),
            reviewed.currency(),
            new BigDecimal("9000"),
            reviewed.liquidity(),
            reviewed.periods(),
            reviewed.rentalContracts(),
            reviewed.maturityDate(),
            reviewed.redemptionValue(),
            reviewed.interestTreatment(),
            reviewed.taxRate(),
            reviewed.taxBase(),
            reviewed.rentalTaxPaidByTenant());
    var reviewedProfile = profile(reviewed);
    var editedProfile =
        profile(edited)
            .withPlanningBaseline(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                reviewed.currentValue(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                reviewedProfile.longTermPlanningState());

    assertThat(projection.cashIncome(editedProfile, assumptions(), 2026))
        .isEqualByComparingTo("80");
  }

  private static ProjectedLongTermAsset asset(
      InterestTreatmentModel treatment,
      BigDecimal annualIncome,
      BigDecimal returnRate,
      BigDecimal taxRate,
      LocalDate maturity) {
    return assetWithPeriods(
        treatment,
        taxRate,
        maturity,
        new ProjectedLongTermAsset.Period(
            LocalDate.of(2020, 1, 1), null, annualIncome, BigDecimal.ZERO, returnRate));
  }

  private static ProjectedLongTermAsset assetWithPeriods(
      InterestTreatmentModel treatment, ProjectedLongTermAsset.Period... periods) {
    return assetWithPeriods(treatment, BigDecimal.ZERO, null, periods);
  }

  private static ProjectedLongTermAsset assetWithPeriods(
      InterestTreatmentModel treatment,
      BigDecimal taxRate,
      LocalDate maturity,
      ProjectedLongTermAsset.Period... periods) {
    return new ProjectedLongTermAsset(
        1L,
        "Bond",
        LongTermAssetTypeModel.BOND,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.PLN,
        new BigDecimal("1000"),
        Liquidity.LIQUID,
        List.of(periods),
        List.of(),
        maturity,
        new BigDecimal("1000"),
        treatment,
        taxRate,
        null,
        false);
  }

  private static ProjectedLongTermAsset.Period period(
      String validFrom, String validTo, String annualIncome, String annualReturnRate) {
    return new ProjectedLongTermAsset.Period(
        LocalDate.parse(validFrom),
        validTo == null ? null : LocalDate.parse(validTo),
        annualIncome == null ? null : new BigDecimal(annualIncome),
        BigDecimal.ZERO,
        new BigDecimal(annualReturnRate));
  }

  private static InvestmentProfile profile(ProjectedLongTermAsset asset) {
    return profile(List.of(asset));
  }

  private static InvestmentProfile profile(List<ProjectedLongTermAsset> assets) {
    BigDecimal value =
        assets.stream()
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        value,
        value,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        value,
        List.of(),
        assets,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private static SimulationAssumptions assumptions() {
    return SimulationAssumptions.defaults(
        profile(
            asset(
                InterestTreatmentModel.PAY_OUT,
                null,
                new BigDecimal("0.10"),
                new BigDecimal("0.20"),
                null)),
        65,
        65,
        2026);
  }
}
