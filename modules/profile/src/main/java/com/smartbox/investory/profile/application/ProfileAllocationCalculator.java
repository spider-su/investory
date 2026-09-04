package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetType;
import com.smartbox.investory.investment.api.portfolio.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Pure allocation rules shared by profile summary and planning composition. */
final class ProfileAllocationCalculator {
  private final BrokerageAssetClassificationReader classifications;

  ProfileAllocationCalculator(BrokerageAssetClassificationReader classifications) {
    this.classifications = classifications;
  }

  Map<AllocationKey, BigDecimal> values(
      SharedBrokeragePortfolioSnapshot market,
      List<LongTermAssetProfileAssetModel> longTermAssets,
      BigDecimal marketCash,
      java.util.function.BiFunction<
              BigDecimal, com.smartbox.investory.shared.currency.CurrencyType, BigDecimal>
          toBase) {
    Map<AllocationKey, BigDecimal> values = new LinkedHashMap<>();
    values.put(new AllocationKey(EconomicBucket.LIQUID_CASH, AssetHorizon.SHORT_TERM), marketCash);
    Map<String, EconomicBucket> marketBuckets = marketBuckets(market);
    for (BrokeragePositionSnapshot position : market.openPositions()) {
      values.merge(
          new AllocationKey(
              marketBuckets.getOrDefault(position.symbol(), EconomicBucket.OTHER),
              AssetHorizon.SHORT_TERM),
          toBase.apply(position.value(), market.baseCurrency()),
          BigDecimal::add);
    }
    for (LongTermAssetProfileAssetModel asset : longTermAssets) {
      values.merge(
          new AllocationKey(classify(asset.type()), AssetHorizon.LONG_TERM),
          toBase.apply(asset.currentValue(), asset.currency()),
          BigDecimal::add);
    }
    return values;
  }

  List<ProfileAllocation> allocations(Map<AllocationKey, BigDecimal> values) {
    List<Map.Entry<AllocationKey, BigDecimal>> entries =
        values.entrySet().stream().filter(entry -> entry.getValue().signum() != 0).toList();
    BigDecimal classifiedTotal =
        entries.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<ProfileAllocation> result = new ArrayList<>();
    for (Map.Entry<AllocationKey, BigDecimal> entry : entries) {
      BigDecimal percentage =
          classifiedTotal.signum() == 0
              ? BigDecimal.ZERO
              : entry.getValue().divide(classifiedTotal, 8, RoundingMode.HALF_UP);
      AllocationKey key = entry.getKey();
      result.add(
          new ProfileAllocation(
              key.bucket(), entry.getValue(), percentage, liquidity(key.bucket()), key.horizon()));
    }
    return List.copyOf(result);
  }

  ProfileAllocationReconciliation.SourceTotal reconciliation(
      Map<AllocationKey, BigDecimal> values, AssetHorizon horizon, BigDecimal authoritativeTotal) {
    BigDecimal classifiedTotal =
        values.entrySet().stream()
            .filter(entry -> entry.getKey().horizon() == horizon)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ProfileAllocationReconciliation.SourceTotal(classifiedTotal, authoritativeTotal);
  }

  EconomicBucket classify(LongTermAssetType type) {
    return switch (type) {
      case REAL_ESTATE -> EconomicBucket.REAL_ESTATE;
      case BOND -> EconomicBucket.FIXED_INCOME;
      case DEPOSIT, CASH_RESERVE -> EconomicBucket.LIQUID_CASH;
      case OTHER -> EconomicBucket.OTHER;
    };
  }

  EconomicBucket classify(BrokerageAssetType type) {
    return switch (type) {
      case EQUITY, ETF, FUND, REIT, INDEX, CRYPTOCURRENCY, COMMODITY -> EconomicBucket.EQUITY;
      case BOND -> EconomicBucket.FIXED_INCOME;
      case CASH -> EconomicBucket.LIQUID_CASH;
      default -> EconomicBucket.OTHER;
    };
  }

  Liquidity liquidity(EconomicBucket bucket) {
    return bucket == EconomicBucket.REAL_ESTATE ? Liquidity.ILLIQUID : Liquidity.LIQUID;
  }

  boolean isContractual(LongTermAssetProfileAssetModel asset) {
    return asset.type() == LongTermAssetType.BOND || asset.type() == LongTermAssetType.DEPOSIT;
  }

  private Map<String, EconomicBucket> marketBuckets(SharedBrokeragePortfolioSnapshot market) {
    Map<String, BrokerageAssetClassification> rows =
        classifications.findBySymbols(
            market.openPositions().stream()
                .map(BrokeragePositionSnapshot::symbol)
                .collect(Collectors.toSet()));
    Map<String, EconomicBucket> result = new HashMap<>();
    rows.forEach((symbol, row) -> result.put(symbol, classify(row.assetType())));
    return result;
  }

  record AllocationKey(EconomicBucket bucket, AssetHorizon horizon) {}
}
