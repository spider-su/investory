package com.smartbox.investory.application.profile;

import com.smartbox.investory.application.longterm.LongTermAssetService;
import com.smartbox.investory.application.longterm.LongTermAssetSummary;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
import com.smartbox.investory.services.models.Portfolio;
import com.smartbox.investory.services.portfolio.read.BrokerageAssetClassification;
import com.smartbox.investory.services.portfolio.read.BrokerageAssetClassificationReader;
import com.smartbox.investory.services.portfolio.read.BrokeragePortfolioReadService;
import com.smartbox.investory.services.portfolio.read.BrokeragePositionSnapshot;
import com.smartbox.investory.services.portfolio.read.SharedBrokeragePortfolioSnapshot;
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
  private final BrokeragePortfolioReadService brokeragePortfolioReadService;
  private final LongTermAssetService longTermAssetService;
  private final BrokerageAssetClassificationReader brokerageAssetClassificationReader;
  private final CurrencyConversion currencyRates;
  private final Clock clock;

  @Transactional(readOnly = true)
  public InvestmentProfile loadProfile(Long portfolioId) {
    LocalDate date = LocalDate.now(clock);
    SharedBrokeragePortfolioSnapshot market = brokeragePortfolioReadService.currentSharedSnapshot();
    LongTermAssetService.AggregateSummary longTerm =
        longTermAssetService.aggregate(portfolioId, date);
    Map<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    values.put(EconomicBucket.LIQUID_CASH, market.cash());
    for (BrokeragePositionSnapshot position : market.openPositions()) {
      EconomicBucket bucket = classify(position.symbol());
      values.merge(bucket, position.value(), BigDecimal::add);
    }
    for (LongTermAssetSummary asset : longTermAssetService.list(portfolioId, date)) {
      EconomicBucket bucket = classify(asset.type());
      BigDecimal value =
          asset.currency() == longTerm.currency()
              ? asset.currentValue()
              : currencyRates.convertToBaseCurrency(
                  asset.currentValue(), longTerm.currency(), asset.currency(), date);
      values.merge(bucket, value, BigDecimal::add);
    }
    BigDecimal marketValue = market.balance();
    BigDecimal longTermValue = longTerm.totalCurrentValue();
    BigDecimal total = marketValue.add(longTermValue);
    BigDecimal marketIncome = market.dividends().add(market.interest());
    BigDecimal longTermIncome = longTerm.annualEconomics().netAnnualIncomeAfterTax();
    List<ProjectedLongTermAsset> manualAssets =
        Optional.ofNullable(longTermAssetService.projectionInputs(portfolioId, date))
            .orElse(List.of())
            .stream()
            .map(
                input ->
                    new ProjectedLongTermAsset(
                        input.id(),
                        input.name(),
                        input.type(),
                        classify(input.type()),
                        longTerm.currency(),
                        convertAmount(
                            input.currentValue(), input.currency(), longTerm.currency(), date),
                        liquidity(classify(input.type())),
                        input.periods().stream()
                            .map(
                                period ->
                                    new ProjectedLongTermAsset.Period(
                                        period.validFrom(),
                                        period.validTo(),
                                        convertAmount(
                                            period.annualIncome(),
                                            input.currency(),
                                            longTerm.currency(),
                                            date),
                                        convertAmount(
                                            period.annualExpense(),
                                            input.currency(),
                                            longTerm.currency(),
                                            date),
                                        period.annualReturnRate(),
                                        period.cashFlowType()))
                            .toList(),
                        input.maturityDate(),
                        input.redemptionValue() == null
                            ? null
                            : convertAmount(
                                input.redemptionValue(),
                                input.currency(),
                                longTerm.currency(),
                                date),
                        input.interestTreatment(),
                        input.taxRate(),
                        input.taxBase() == null
                            ? null
                            : convertAmount(
                                input.taxBase(), input.currency(), longTerm.currency(), date)))
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
        longTerm.currency(),
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

  public Portfolio loadMarketInvestments(Long portfolioId) {
    return brokeragePortfolioReadService.currentMarketInvestments();
  }

  public List<LongTermAssetSummary> loadLongTermAssets(Long portfolioId) {
    return longTermAssetService.list(portfolioId, LocalDate.now(clock));
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

  private BigDecimal convertAmount(
      BigDecimal value, CurrencyType from, CurrencyType to, java.time.LocalDate date) {
    return from == to ? value : currencyRates.convertToBaseCurrency(value, to, from, date);
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
