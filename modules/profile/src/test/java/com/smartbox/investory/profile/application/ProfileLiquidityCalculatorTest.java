package com.smartbox.investory.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileLiquidityCalculatorTest {
  private static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);

  @Test
  void separatesBrokerageCashAndInvestedCapitalFromIlliquidAndContractualAssets() {
    var calculator = new ProfileLiquidityCalculator(new ProfileCurrencyNormalizer(mock()));
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values = new LinkedHashMap<>();
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.LIQUID_CASH, AssetHorizon.SHORT_TERM, Liquidity.LIQUID),
        new BigDecimal("100"));
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.EQUITY, AssetHorizon.SHORT_TERM, Liquidity.LIQUID),
        new BigDecimal("500"));
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.REAL_ESTATE, AssetHorizon.LONG_TERM, Liquidity.ILLIQUID),
        new BigDecimal("1000"));
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.FIXED_INCOME, AssetHorizon.LONG_TERM, Liquidity.ILLIQUID),
        new BigDecimal("200"));
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.LIQUID_CASH, AssetHorizon.LONG_TERM, Liquidity.LIQUID),
        new BigDecimal("350"));
    List<LongTermAssetProfileAssetModel> longTermAssets =
        List.of(
            new LongTermAssetProfileAssetModel(
                AssetEconomicCategory.REAL_ESTATE, CurrencyType.USD, new BigDecimal("1000"), false),
            new LongTermAssetProfileAssetModel(
                AssetEconomicCategory.FIXED_INCOME, CurrencyType.USD, new BigDecimal("200"), false),
            new LongTermAssetProfileAssetModel(
                AssetEconomicCategory.LIQUID_CASH, CurrencyType.USD, new BigDecimal("350"), true));

    var result =
        calculator.calculate(
            values,
            longTermAssets,
            new BigDecimal("100"),
            new BigDecimal("600"),
            CurrencyType.USD,
            AS_OF);

    assertThat(result.liquid()).isEqualByComparingTo("950");
    assertThat(result.illiquid()).isEqualByComparingTo("1200");
    assertThat(result.reserve()).isEqualByComparingTo("450");
    // investmentCapital means invested brokerage market value, excluding brokerage cash.
    assertThat(result.investmentCapital()).isEqualByComparingTo("500");
  }

  @Test
  void excludesLockedCashFromLiquidAssetsAndRetirementReserve() {
    var calculator = new ProfileLiquidityCalculator(new ProfileCurrencyNormalizer(mock()));
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values =
        Map.of(
            new ProfileAllocationCalculator.AllocationKey(
                EconomicBucket.LIQUID_CASH, AssetHorizon.LONG_TERM, Liquidity.ILLIQUID),
            new BigDecimal("1000"));
    var locked =
        new LongTermAssetProfileAssetModel(
            AssetEconomicCategory.LIQUID_CASH, CurrencyType.USD, new BigDecimal("1000"), false);

    var result =
        calculator.calculate(
            values, List.of(locked), BigDecimal.ZERO, BigDecimal.ZERO, CurrencyType.USD, AS_OF);

    assertThat(result.liquid()).isZero();
    assertThat(result.illiquid()).isEqualByComparingTo("1000");
    assertThat(result.reserve()).isZero();
  }

  @Test
  void negativeBrokerageCashDoesNotConsumeAnAvailableLongTermReserve() {
    var calculator = new ProfileLiquidityCalculator(new ProfileCurrencyNormalizer(mock()));
    var availableReserve =
        new LongTermAssetProfileAssetModel(
            AssetEconomicCategory.LIQUID_CASH, CurrencyType.USD, new BigDecimal("350"), true);
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values = new LinkedHashMap<>();
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.LIQUID_CASH, AssetHorizon.SHORT_TERM, Liquidity.LIQUID),
        new BigDecimal("-100"));
    values.put(
        new ProfileAllocationCalculator.AllocationKey(
            EconomicBucket.LIQUID_CASH, AssetHorizon.LONG_TERM, Liquidity.LIQUID),
        new BigDecimal("350"));

    var result =
        calculator.calculate(
            values,
            List.of(availableReserve),
            new BigDecimal("-100"),
            new BigDecimal("400"),
            CurrencyType.USD,
            AS_OF);

    assertThat(result.liquid()).isEqualByComparingTo("250");
    assertThat(result.reserve()).isEqualByComparingTo("350");
    assertThat(result.investmentCapital()).isEqualByComparingTo("500");
  }
}
