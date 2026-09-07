package com.smartbox.investory.profile.application;

import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Pure liquidity, reserve, and investable-capital rules for the profile summary. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProfileLiquidityCalculator {
  private final ProfileCurrencyNormalizer currencyNormalizer;

  Result calculate(
      Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values,
      List<LongTermAssetProfileAssetModel> longTermAssets,
      BigDecimal marketCash,
      BigDecimal marketValue,
      CurrencyType base,
      LocalDate date) {
    BigDecimal liquid = total(values, Liquidity.LIQUID);
    BigDecimal illiquid = total(values, Liquidity.ILLIQUID);
    BigDecimal reserve = marketCash.max(BigDecimal.ZERO);
    for (LongTermAssetProfileAssetModel asset : longTermAssets) {
      if (asset.category() == com.smartbox.investory.shared.assets.AssetEconomicCategory.LIQUID_CASH
          && asset.fundingAvailable()) {
        reserve =
            reserve.add(
                currencyNormalizer.toBase(asset.currentValue(), asset.currency(), base, date));
      }
    }
    return new Result(
        liquid,
        illiquid,
        reserve.max(BigDecimal.ZERO),
        marketValue.subtract(marketCash).max(BigDecimal.ZERO));
  }

  private BigDecimal total(
      Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values, Liquidity liquidity) {
    return values.entrySet().stream()
        .filter(entry -> entry.getKey().liquidity() == liquidity)
        .map(Map.Entry::getValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  record Result(
      BigDecimal liquid, BigDecimal illiquid, BigDecimal reserve, BigDecimal investmentCapital) {}
}
