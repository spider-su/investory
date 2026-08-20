package com.smartbox.investory.retirement.api;

import com.smartbox.investory.investment.api.BrokerageAssetClassification;
import com.smartbox.investory.investment.api.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetProfileAsset;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummary;
import com.smartbox.investory.longterm.api.LongTermAssetProjection;
import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentProfileFacade {
  private final BrokeragePortfolioReader brokeragePortfolioReadService;
  private final LongTermAssetProfileReader longTermAssets;
  private final BrokerageAssetClassificationReader brokerageAssetClassificationReader;
  private final CurrencyConversion currencyRates;
  private final Clock clock;

  @Transactional(readOnly = true)
  public InvestmentProfile loadProfile(Long portfolioId) {
    LocalDate date = LocalDate.now(clock);
    SharedBrokeragePortfolioSnapshot market = brokeragePortfolioReadService.currentSharedSnapshot();
    LongTermAssetProfileSummary longTerm = longTermAssets.aggregate(portfolioId, date);
    Map<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    BigDecimal marketCash = toUsd(market.cash(), market.baseCurrency(), date);
    values.put(EconomicBucket.LIQUID_CASH, marketCash);
    for (BrokeragePositionSnapshot position : market.openPositions()) {
      EconomicBucket bucket = classify(position.symbol());
      values.merge(bucket, toUsd(position.value(), market.baseCurrency(), date), BigDecimal::add);
    }
    for (LongTermAssetProfileAsset asset : longTermAssets.list(portfolioId, date)) {
      EconomicBucket bucket = classify(asset.type());
      BigDecimal value = asset.currentValue();
      values.merge(bucket, value, BigDecimal::add);
    }
    BigDecimal marketValue = toUsd(market.balance(), market.baseCurrency(), date);
    BigDecimal longTermValue = longTerm.totalCurrentValue();
    BigDecimal total = marketValue.add(longTermValue);
    BigDecimal marketIncome =
        toUsd(market.dividends(), market.baseCurrency(), date)
            .add(toUsd(market.interest(), market.baseCurrency(), date));
    BigDecimal longTermIncome = longTerm.netAnnualIncomeAfterTax();
    List<ProjectedLongTermAsset> manualAssets =
        Optional.ofNullable(longTermAssets.projectionInputs(portfolioId, date))
            .orElse(List.of())
            .stream()
            .map(
                (LongTermAssetProjection input) ->
                    new ProjectedLongTermAsset(
                        input.id(),
                        input.name(),
                        input.type(),
                        classify(input.type()),
                        CurrencyType.USD,
                        input.currentValue(),
                        liquidity(classify(input.type())),
                        input.periods().stream()
                            .map(
                                period ->
                                    new ProjectedLongTermAsset.Period(
                                        period.validFrom(),
                                        period.validTo(),
                                        period.annualIncome(),
                                        period.annualExpense(),
                                        period.annualReturnRate(),
                                        period.cashFlowType(),
                                        period.paidByTenant()))
                            .toList(),
                        input.maturityDate(),
                        input.redemptionValue() == null ? null : input.redemptionValue(),
                        input.interestTreatment(),
                        input.taxRate(),
                        input.taxBase() == null ? null : input.taxBase(),
                        input.rentalTaxPaidByTenant()))
            .toList();
    BigDecimal lockedContractual =
        manualAssets.stream()
            .filter(this::isContractual)
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal liquid =
        values.entrySet().stream()
            .filter(e -> liquidity(e.getKey()) == Liquidity.LIQUID)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .subtract(lockedContractual)
            .max(BigDecimal.ZERO);
    BigDecimal illiquid =
        values.entrySet().stream()
            .filter(e -> liquidity(e.getKey()) == Liquidity.ILLIQUID)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(lockedContractual);
    return new InvestmentProfile(
        portfolioId,
        CurrencyType.USD,
        marketValue,
        longTermValue,
        total,
        marketIncome,
        longTermIncome,
        marketIncome.add(longTermIncome),
        liquid,
        illiquid,
        allocations(values, total),
        manualAssets);
  }

  public List<LongTermAssetProfileAsset> loadLongTermAssets(Long portfolioId) {
    return longTermAssets.list(portfolioId, LocalDate.now(clock));
  }

  public List<ProfileAllocation> loadAllocation(Long portfolioId) {
    return loadProfile(portfolioId).allocations();
  }

  private EconomicBucket classify(String symbol) {
    return brokerageAssetClassificationReader
        .findBySymbol(symbol)
        .map(BrokerageAssetClassification::assetType)
        .map(this::classifyAssetType)
        .orElse(EconomicBucket.OTHER);
  }

  private EconomicBucket classifyAssetType(String type) {
    if (type == null) return EconomicBucket.OTHER;
    return switch (type.toUpperCase(Locale.ROOT)) {
      case "EQUITY", "ETF", "FUND", "REIT", "INDEX", "CRYPTOCURRENCY", "COMMODITY" ->
          EconomicBucket.EQUITY;
      case "BOND" -> EconomicBucket.FIXED_INCOME;
      case "CASH" -> EconomicBucket.LIQUID_CASH;
      default -> EconomicBucket.OTHER;
    };
  }

  private EconomicBucket classify(LongTermAssetType type) {
    return switch (type) {
      case REAL_ESTATE -> EconomicBucket.REAL_ESTATE;
      case BOND -> EconomicBucket.FIXED_INCOME;
      case DEPOSIT, CASH_RESERVE -> EconomicBucket.LIQUID_CASH;
      case OTHER -> EconomicBucket.OTHER;
    };
  }

  private boolean isContractual(ProjectedLongTermAsset asset) {
    return asset.type() == LongTermAssetType.BOND || asset.type() == LongTermAssetType.DEPOSIT;
  }

  private BigDecimal toUsd(BigDecimal value, CurrencyType source, java.time.LocalDate date) {
    return source == CurrencyType.USD
        ? value
        : currencyRates.convertToBaseCurrency(value, CurrencyType.USD, source, date);
  }

  private Liquidity liquidity(EconomicBucket bucket) {
    return bucket == EconomicBucket.REAL_ESTATE ? Liquidity.ILLIQUID : Liquidity.LIQUID;
  }

  private List<ProfileAllocation> allocations(
      Map<EconomicBucket, BigDecimal> values, BigDecimal total) {
    return Arrays.stream(EconomicBucket.values())
        .map(
            bucket ->
                new ProfileAllocation(
                    bucket,
                    values.getOrDefault(bucket, BigDecimal.ZERO),
                    total.signum() == 0
                        ? BigDecimal.ZERO
                        : values
                            .getOrDefault(bucket, BigDecimal.ZERO)
                            .divide(total, 8, RoundingMode.HALF_UP),
                    liquidity(bucket)))
        .toList();
  }

  private static BigDecimal money(double value) {
    return BigDecimal.valueOf(value);
  }
}
