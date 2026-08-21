package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractualAssetProjectorTest {
  private static final SimulationAssumptions ASSUMPTIONS =
      SimulationAssumptions.defaults(null, 40, 80);

  @Test
  void explicitRatePayoutTaxAndPartialYearAreSharedMechanics() {
    ProjectedLongTermAsset asset = asset(InterestTreatment.PAY_OUT, null, null, "0.20");
    ContractualAssetProjector.Projection result =
        ContractualAssetProjector.project(
            asset, bd("1000"), ASSUMPTIONS, 2027, LocalDate.of(2026, 12, 31), bd("0.50"));

    assertEquals(bd("1000"), result.endValue());
    assertEquals(0, bd("20.000").compareTo(result.payoutIncome()));
    assertEquals(BigDecimal.ZERO, result.redemptionCash());
  }

  @Test
  void capitalizeInterestAndRedemptionUseTheSamePrincipalRule() {
    ProjectedLongTermAsset asset =
        asset(InterestTreatment.CAPITALIZE, LocalDate.of(2027, 12, 31), bd("1200"), "0.10");
    ContractualAssetProjector.Projection result =
        ContractualAssetProjector.project(
            asset, bd("1000"), ASSUMPTIONS, 2027, LocalDate.of(2026, 12, 31), BigDecimal.ONE);

    assertEquals(BigDecimal.ZERO, result.endValue());
    assertEquals(BigDecimal.ZERO, result.payoutIncome());
    assertEquals(bd("1200"), result.redemptionCash());
  }

  @Test
  void explicitZeroRateWinsOverScenarioFallback() {
    ProjectedLongTermAsset asset =
        new ProjectedLongTermAsset(
            1L,
            "bond",
            LongTermAssetType.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.USD,
            bd("1000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2027, 1, 1),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)),
            LocalDate.of(2030, 12, 31),
            null,
            InterestTreatment.CAPITALIZE,
            BigDecimal.ZERO);

    ContractualAssetProjector.Projection result =
        ContractualAssetProjector.project(
            asset, bd("1000"), ASSUMPTIONS, 2027, LocalDate.of(2026, 12, 31), BigDecimal.ONE);

    assertEquals(bd("1000"), result.endValue());
  }

  private static ProjectedLongTermAsset asset(
      InterestTreatment treatment, LocalDate maturity, BigDecimal redemption, String taxRate) {
    return new ProjectedLongTermAsset(
        1L,
        "bond",
        LongTermAssetType.BOND,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.USD,
        bd("1000"),
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2027, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO, bd("0.05"))),
        maturity,
        redemption,
        treatment,
        bd(taxRate));
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
