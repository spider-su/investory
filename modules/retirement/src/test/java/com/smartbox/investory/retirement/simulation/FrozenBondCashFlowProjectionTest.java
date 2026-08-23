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
  void payOutIncomeIsTaxedAndStopsAfterMaturity() {
    var asset =
        asset(
            InterestTreatmentModel.PAY_OUT,
            null,
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
            null,
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
                period("2024-01-01", "2026-12-31", null, "0.03"),
                period("2027-01-01", "2030-12-31", null, "0.05")));

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
                period("2027-01-01", "2030-12-31", null, "0.05"),
                period("2024-01-01", "2026-12-31", null, "0.03")));

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
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        BigDecimal.ZERO,
        asset.currentValue(),
        asset.currentValue(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        asset.currentValue(),
        List.of(),
        List.of(asset),
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
