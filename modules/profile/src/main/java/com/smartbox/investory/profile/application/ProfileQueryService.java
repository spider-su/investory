package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetType;
import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.ProfileReader;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProfilePlanning;
import com.smartbox.investory.profile.api.model.ProfileSummary;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileQueryService implements ProfileReader {
  private final BrokeragePortfolioReader brokeragePortfolioReadService;
  private final LongTermAssetProfileReader longTermAssets;
  private final BrokerageAssetClassificationReader brokerageAssetClassificationReader;
  private final CurrencyConversion currencyRates;
  private final Clock clock;

  @Transactional(readOnly = true)
  @Override
  public InvestmentProfile loadProfile(Long portfolioId) {
    return buildProfile(portfolioId);
  }

  @Override
  public ProfileSummary loadSummary(Long portfolioId) {
    return ProfileSummary.from(buildProfile(portfolioId));
  }

  @Override
  public ProfilePlanning loadPlanning(Long portfolioId) {
    return new ProfilePlanning(buildProfile(portfolioId).longTermPlanningState());
  }

  private InvestmentProfile buildProfile(Long portfolioId) {
    LocalDate date = LocalDate.now(clock);
    SharedBrokeragePortfolioSnapshot market =
        brokeragePortfolioReadService.currentSnapshot(portfolioId);
    LongTermAssetProfileSnapshotModel longTermSnapshot = longTermAssets.snapshot(portfolioId, date);
    LongTermAssetProfileSummaryModel longTerm = longTermSnapshot.summary();
    List<LongTermAssetProfileAssetModel> longTermAssetRows = longTermSnapshot.assets();
    List<LongTermAssetProjectionModel> projectionInputs = longTermSnapshot.projectionInputs();
    Map<AllocationKey, BigDecimal> values = new LinkedHashMap<>();
    BigDecimal marketCash = toUsd(market.cash(), market.baseCurrency(), date);
    values.put(new AllocationKey(EconomicBucket.LIQUID_CASH, AssetHorizon.SHORT_TERM), marketCash);
    Map<String, EconomicBucket> marketBuckets = marketBuckets(market);
    for (BrokeragePositionSnapshot position : market.openPositions()) {
      EconomicBucket bucket = marketBuckets.getOrDefault(position.symbol(), EconomicBucket.OTHER);
      values.merge(
          new AllocationKey(bucket, AssetHorizon.SHORT_TERM),
          toUsd(position.value(), market.baseCurrency(), date),
          BigDecimal::add);
    }
    for (LongTermAssetProfileAssetModel asset : longTermAssetRows) {
      EconomicBucket bucket = classify(asset.type());
      BigDecimal value = asset.currentValue();
      values.merge(new AllocationKey(bucket, AssetHorizon.LONG_TERM), value, BigDecimal::add);
    }
    BigDecimal marketValue = toUsd(market.balance(), market.baseCurrency(), date);
    BigDecimal longTermValue = longTerm.totalCurrentValue();
    ProfileAllocationReconciliation allocationReconciliation =
        new ProfileAllocationReconciliation(
            reconcileAllocation(values, AssetHorizon.SHORT_TERM, marketValue),
            reconcileAllocation(values, AssetHorizon.LONG_TERM, longTermValue));
    BigDecimal total = marketValue.add(longTermValue);
    BrokerageIncomeSnapshot incomeSnapshot =
        brokeragePortfolioReadService.incomeForMonths(
            portfolioId, YearMonth.of(date.getYear(), 1), YearMonth.from(date));
    CurrencyType incomeCurrency =
        incomeSnapshot == null || incomeSnapshot.baseCurrency() == null
            ? market.baseCurrency()
            : incomeSnapshot.baseCurrency();
    BigDecimal marketIncome =
        incomeSnapshot == null
            ? toUsd(market.dividends(), market.baseCurrency(), date)
                .add(toUsd(market.interest(), market.baseCurrency(), date))
            : toUsd(incomeSnapshot.netIncome(), incomeCurrency, date);
    BigDecimal projectedMarketIncome = annualize(marketIncome, incomeSnapshot, date);
    BigDecimal marketIncomeBasis =
        marketIncomeBasis(incomeSnapshot, incomeCurrency, marketValue, date);
    BigDecimal longTermIncome = longTerm.netAnnualIncomeAfterTax();
    ProfileIncomeSummary incomeSummary =
        new ProfileIncomeSummary(
            marketIncome,
            projectedMarketIncome,
            ProfileIncomeSummary.ratio(projectedMarketIncome, marketIncomeBasis),
            longTermIncome,
            ProfileIncomeSummary.ratio(longTermIncome, longTermValue),
            projectedMarketIncome.add(longTermIncome),
            ProfileIncomeSummary.ratio(projectedMarketIncome.add(longTermIncome), total));
    BigDecimal explicitReserve = marketCash;
    for (LongTermAssetProfileAssetModel asset : longTermAssetRows) {
      if (asset.type() == LongTermAssetType.CASH_RESERVE) {
        explicitReserve = explicitReserve.add(asset.currentValue());
      }
    }
    BigDecimal brokerageInvestmentCapital = marketValue.subtract(marketCash).max(BigDecimal.ZERO);
    LongTermAssetAnnualSnapshotModel annualFacts = longTermSnapshot.annualSnapshot();
    ProfileAssetProjection planningState = planningState(projectionInputs, date);
    List<ProjectedLongTermAsset> manualAssets = planningState.assets();
    BigDecimal lockedContractual =
        manualAssets.stream()
            .filter(this::isContractual)
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal liquid =
        values.entrySet().stream()
            .filter(e -> liquidity(e.getKey().bucket()) == Liquidity.LIQUID)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .subtract(lockedContractual)
            .max(BigDecimal.ZERO);
    BigDecimal illiquid =
        values.entrySet().stream()
            .filter(e -> liquidity(e.getKey().bucket()) == Liquidity.ILLIQUID)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(lockedContractual);
    return new InvestmentProfile(
        portfolioId,
        CurrencyType.USD,
        marketValue,
        longTermValue,
        total,
        liquid,
        illiquid,
        allocations(values, total),
        annualFacts.rentalIncome(),
        annualFacts.bondIncome(),
        planningState,
        explicitReserve,
        brokerageInvestmentCapital,
        incomeSummary,
        allocationReconciliation);
  }

  private Map<String, EconomicBucket> marketBuckets(SharedBrokeragePortfolioSnapshot market) {
    Map<String, BrokerageAssetClassification> classifications =
        brokerageAssetClassificationReader.findBySymbols(
            market.openPositions().stream()
                .map(BrokeragePositionSnapshot::symbol)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)));
    Map<String, EconomicBucket> result = new HashMap<>();
    classifications.forEach(
        (symbol, classification) ->
            result.put(symbol, classifyAssetType(classification.assetType())));
    return result;
  }

  private EconomicBucket classifyAssetType(BrokerageAssetType type) {
    return switch (type) {
      case EQUITY, ETF, FUND, REIT, INDEX, CRYPTOCURRENCY, COMMODITY -> EconomicBucket.EQUITY;
      case BOND -> EconomicBucket.FIXED_INCOME;
      case CASH -> EconomicBucket.LIQUID_CASH;
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

  private ProjectedLongTermAsset toProfileAsset(LongTermAssetProjectionModel input) {
    EconomicBucket bucket = classify(input.type());
    return new ProjectedLongTermAsset(
        input.id(),
        input.name(),
        input.type(),
        bucket,
        CurrencyType.USD,
        input.currentValue(),
        liquidity(bucket),
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
        input.rentalContracts(),
        input.maturityDate(),
        input.redemptionValue(),
        input.interestTreatment(),
        input.taxRate(),
        input.taxBase(),
        input.rentalTaxPaidByTenant());
  }

  private ProfileAssetProjection planningState(
      List<LongTermAssetProjectionModel> projectionInputs, LocalDate date) {
    return new ProfileAssetProjection(
        projectionInputs.stream().map(this::toProfileAsset).toList(),
        BigDecimal.ZERO,
        date.getYear(),
        ProjectionSource.PROJECTED);
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
      Map<AllocationKey, BigDecimal> values, BigDecimal total) {
    List<Map.Entry<AllocationKey, BigDecimal>> entries =
        values.entrySet().stream().filter(entry -> entry.getValue().signum() != 0).toList();
    List<ProfileAllocation> result = new ArrayList<>();
    BigDecimal assignedPercentage = BigDecimal.ZERO;
    for (int index = 0; index < entries.size(); index++) {
      var entry = entries.get(index);
      AllocationKey key = entry.getKey();
      BigDecimal value = entry.getValue();
      BigDecimal percentage =
          total.signum() == 0
              ? BigDecimal.ZERO
              : index == entries.size() - 1
                  ? BigDecimal.ONE.subtract(assignedPercentage)
                  : value.divide(total, 8, RoundingMode.HALF_UP);
      assignedPercentage = assignedPercentage.add(percentage);
      result.add(
          new ProfileAllocation(
              key.bucket(), value, percentage, liquidity(key.bucket()), key.horizon()));
    }
    return List.copyOf(result);
  }

  private ProfileAllocationReconciliation.SourceTotal reconcileAllocation(
      Map<AllocationKey, BigDecimal> values, AssetHorizon horizon, BigDecimal authoritativeTotal) {
    BigDecimal classifiedTotal =
        values.entrySet().stream()
            .filter(entry -> entry.getKey().horizon() == horizon)
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ProfileAllocationReconciliation.SourceTotal reconciliation =
        new ProfileAllocationReconciliation.SourceTotal(classifiedTotal, authoritativeTotal);
    if (reconciliation.delta().signum() != 0) {
      values.merge(
          new AllocationKey(EconomicBucket.OTHER, horizon),
          reconciliation.delta(),
          BigDecimal::add);
    }
    return reconciliation;
  }

  private record AllocationKey(EconomicBucket bucket, AssetHorizon horizon) {}

  private BigDecimal annualize(
      BigDecimal income, BrokerageIncomeSnapshot snapshot, LocalDate asOfDate) {
    LocalDate yearStart = LocalDate.of(asOfDate.getYear(), 1, 1);
    LocalDate start =
        snapshot == null || snapshot.periodStart() == null
            ? yearStart
            : snapshot.periodStart().isBefore(yearStart) ? yearStart : snapshot.periodStart();
    LocalDate end =
        snapshot == null || snapshot.periodEnd() == null || snapshot.periodEnd().isAfter(asOfDate)
            ? asOfDate
            : snapshot.periodEnd();
    if (end.isBefore(start)) return BigDecimal.ZERO;
    long observedDays = ChronoUnit.DAYS.between(start, end) + 1;
    return income
        .multiply(BigDecimal.valueOf(asOfDate.lengthOfYear()))
        .divide(BigDecimal.valueOf(observedDays), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal marketIncomeBasis(
      BrokerageIncomeSnapshot snapshot,
      CurrencyType sourceCurrency,
      BigDecimal fallback,
      LocalDate date) {
    if (snapshot == null) return fallback;
    BigDecimal start = toUsd(snapshot.startValue(), sourceCurrency, date);
    BigDecimal end = toUsd(snapshot.endValue(), sourceCurrency, date);
    if (start.signum() > 0 && end.signum() > 0) {
      return start.add(end).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }
    if (end.signum() > 0) return end;
    if (start.signum() > 0) return start;
    return fallback;
  }
}
