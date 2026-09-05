package com.smartbox.investory.profile.application;

import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Pure liquidity, reserve, and investable-capital rules for the profile summary. */
final class ProfileLiquidityCalculator {
  private final CurrencyConversion currencyRates;

  ProfileLiquidityCalculator(CurrencyConversion currencyRates) {
    this.currencyRates = currencyRates;
  }

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
        reserve = reserve.add(toBase(asset.currentValue(), asset.currency(), base, date));
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

  private BigDecimal toBase(
      BigDecimal value, CurrencyType source, CurrencyType target, LocalDate date) {
    return value == null || source == target
        ? value == null ? BigDecimal.ZERO : value
        : currencyRates.convertToBaseCurrency(value, target, source, date);
  }

  record Result(
      BigDecimal liquid, BigDecimal illiquid, BigDecimal reserve, BigDecimal investmentCapital) {}
}
