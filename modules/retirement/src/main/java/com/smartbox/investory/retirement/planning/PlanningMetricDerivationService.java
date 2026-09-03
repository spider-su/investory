package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioActualsReader;
import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioYear;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.*;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Derives planning metrics from authoritative historical and profile sources. */
@Service
public class PlanningMetricDerivationService {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final HistoricalPortfolioActualsReader historicalPortfolio;
  private final HistoricalLongTermAssetYearSource historicalLongTermAssets;

  public PlanningMetricDerivationService(
      HistoricalPortfolioActualsReader historicalPortfolio,
      HistoricalLongTermAssetYearSource historicalLongTermAssets) {
    this.historicalPortfolio = historicalPortfolio;
    this.historicalLongTermAssets = historicalLongTermAssets;
  }

  public HistoricalPortfolioYear historicalPortfolio(Long portfolioId, int year) {
    return historicalPortfolio.read(portfolioId, year);
  }

  public Map<PlanningMetric, PlanningMetricValue> historicalMarket(Long portfolioId, int year) {
    HistoricalPortfolioYear source = historicalPortfolio(portfolioId, year);
    if (!source.complete()) return Map.of();
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    put(
        result,
        PlanningMetric.MARKET_ASSETS,
        source.endMarketAssets(),
        PlanningValueSource.ACCOUNTING_DERIVED);
    put(
        result,
        PlanningMetric.MARKET_INCOME,
        source.marketIncome(),
        PlanningValueSource.ACCOUNTING_DERIVED);
    put(
        result,
        PlanningMetric.MARKET_WITHDRAWAL,
        source.netWithdrawal(),
        PlanningValueSource.ACCOUNTING_DERIVED);
    if (source.marketReturn() != null)
      put(
          result,
          PlanningMetric.MARKET_RETURN,
          source.marketReturn(),
          PlanningValueSource.ACCOUNTING_DERIVED);
    return result;
  }

  public Map<PlanningMetric, PlanningMetricValue> historicalLongTermAssets(
      Long portfolioId, int year) {
    if (historicalLongTermAssets == null) return Map.of();
    var source = historicalLongTermAssets.read(portfolioId, year);
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    putAvailable(
        result,
        PlanningMetric.RENTAL_INCOME,
        source.rentalIncomeAvailable(),
        source.rentalIncome());
    putAvailable(
        result,
        PlanningMetric.REAL_ESTATE,
        source.realEstateValueAvailable(),
        source.realEstateValue());
    putAvailable(
        result, PlanningMetric.BOND_VALUE, source.bondValueAvailable(), source.bondValue());
    putAvailable(
        result, PlanningMetric.BOND_INCOME, source.bondIncomeAvailable(), source.bondIncome());
    putAvailable(
        result,
        PlanningMetric.CASH_RESERVE_VALUE,
        source.cashReserveValueAvailable(),
        source.cashReserveValue());
    return result;
  }

  public Map<PlanningMetric, PlanningMetricValue> currentActual(InvestmentProfile profile) {
    Map<EconomicBucket, BigDecimal> allocation = new EnumMap<>(EconomicBucket.class);
    profile
        .allocations()
        .forEach(value -> allocation.merge(value.bucket(), value.value(), BigDecimal::add));
    BigDecimal manualReserve = longTermAssetsTotal(profile, LongTermAssetType.CASH_RESERVE);
    BigDecimal locked =
        longTermAssetsTotal(profile, LongTermAssetType.BOND)
            .add(longTermAssetsTotal(profile, LongTermAssetType.DEPOSIT));
    BigDecimal fixed =
        allocation
            .getOrDefault(EconomicBucket.FIXED_INCOME, ZERO)
            .subtract(longTermAssetsTotal(profile, LongTermAssetType.BOND))
            .max(ZERO);
    BigDecimal safeReserve =
        allocation
            .getOrDefault(EconomicBucket.LIQUID_CASH, ZERO)
            .add(allocation.getOrDefault(EconomicBucket.FIXED_INCOME, ZERO))
            .subtract(locked)
            .max(ZERO);
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    put(
        result,
        PlanningMetric.NET_WORTH,
        profile.totalNetWorth(),
        PlanningValueSource.PORTFOLIO_DERIVED);
    put(
        result,
        PlanningMetric.MARKET_ASSETS,
        profile.marketPortfolioValue(),
        PlanningValueSource.PORTFOLIO_DERIVED);
    put(result, PlanningMetric.SAFE_RESERVE, safeReserve, PlanningValueSource.PORTFOLIO_DERIVED);
    put(
        result,
        PlanningMetric.CASH_RESERVE_VALUE,
        profile.retirementReserve(),
        PlanningValueSource.PORTFOLIO_DERIVED);
    put(
        result,
        PlanningMetric.MANUAL_LIQUID_RESERVE,
        manualReserve,
        PlanningValueSource.LONG_TERM_DERIVED);
    put(result, PlanningMetric.FIXED_INCOME, fixed, PlanningValueSource.PORTFOLIO_DERIVED);
    put(
        result,
        PlanningMetric.EQUITY,
        allocation.getOrDefault(EconomicBucket.EQUITY, ZERO),
        PlanningValueSource.PORTFOLIO_DERIVED);
    put(
        result,
        PlanningMetric.REAL_ESTATE,
        allocation.getOrDefault(EconomicBucket.REAL_ESTATE, ZERO),
        PlanningValueSource.LONG_TERM_DERIVED);
    return result;
  }

  private static void put(
      Map<PlanningMetric, PlanningMetricValue> result,
      PlanningMetric metric,
      BigDecimal value,
      PlanningValueSource source) {
    result.put(metric, new PlanningMetricValue(metric, value, null, source, null));
  }

  /** Long-Term assets already normalized by Profile; used only for Retirement classifications. */
  private static BigDecimal longTermAssetsTotal(InvestmentProfile profile, LongTermAssetType type) {
    return profile.longTermPlanningState().assets().stream()
        .filter(asset -> asset.type() == type)
        .map(ProjectedLongTermAsset::currentValue)
        .reduce(ZERO, BigDecimal::add);
  }

  private static void putAvailable(
      Map<PlanningMetric, PlanningMetricValue> result,
      PlanningMetric metric,
      boolean available,
      BigDecimal value) {
    result.put(
        metric,
        available
            ? new PlanningMetricValue(
                metric, value, null, PlanningValueSource.LONG_TERM_DERIVED, null)
            : PlanningTimelineValueSupport.unavailable(metric));
  }
}
