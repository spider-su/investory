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
  private final ProfileAllocationCalculator allocations;
  private final CurrencyConversion currencyRates;

  ProfileLiquidityCalculator(
      ProfileAllocationCalculator allocations, CurrencyConversion currencyRates) {
    this.allocations = allocations;
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
    BigDecimal lockedContractual =
        longTermAssets.stream()
            .filter(allocations::isContractual)
            .map(asset -> toBase(asset.currentValue(), asset.currency(), base, date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    liquid = liquid.subtract(lockedContractual).max(BigDecimal.ZERO);
    illiquid = illiquid.add(lockedContractual);
    BigDecimal reserve = marketCash;
    for (LongTermAssetProfileAssetModel asset : longTermAssets) {
      if (asset.type()
          == com.smartbox.investory.longterm.api.model.LongTermAssetType.CASH_RESERVE) {
        reserve = reserve.add(toBase(asset.currentValue(), asset.currency(), base, date));
      }
    }
    return new Result(
        liquid, illiquid, reserve, marketValue.subtract(marketCash).max(BigDecimal.ZERO));
  }

  private BigDecimal total(
      Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values, Liquidity liquidity) {
    return values.entrySet().stream()
        .filter(entry -> allocations.liquidity(entry.getKey().bucket()) == liquidity)
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
