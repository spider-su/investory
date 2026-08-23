package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.investment.api.HistoricalPortfolioActualsReader;
import com.smartbox.investory.investment.api.HistoricalPortfolioYear;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Compares frozen planning values only with trustworthy historical portfolio reporting data. */
@Service
public class PlanningReconciliationService {
  private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01");
  private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.00000001");
  private final HistoricalPortfolioActualsReader historicalPortfolio;

  @Autowired
  public PlanningReconciliationService(HistoricalPortfolioActualsReader historicalPortfolio) {
    this.historicalPortfolio = historicalPortfolio;
  }

  public HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear) {
    Map<PlanningMetric, PlanningMetricValue> accounting =
        accountingValues(portfolioId, planningYear.year());
    List<PlanningMetric> supported =
        List.of(
            PlanningMetric.MARKET_ASSETS,
            PlanningMetric.MARKET_INCOME,
            PlanningMetric.MARKET_WITHDRAWAL,
            PlanningMetric.MARKET_RETURN);
    return new HistoricalReconciliation(
        supported.stream()
            .filter(
                metric ->
                    planningYear.values().containsKey(metric) || accounting.containsKey(metric))
            .map(
                metric ->
                    compare(metric, planningYear.values().get(metric), accounting.get(metric)))
            .toList());
  }

  private PlanningMetricReconciliation compare(
      PlanningMetric metric, PlanningMetricValue planning, PlanningMetricValue reference) {
    BigDecimal planningValue = planning == null ? null : planning.value();
    BigDecimal referenceValue = reference == null ? null : reference.value();
    BigDecimal tolerance =
        metric == PlanningMetric.MARKET_RETURN ? RATE_TOLERANCE : MONEY_TOLERANCE;
    if (planningValue == null || referenceValue == null)
      return new PlanningMetricReconciliation(
          metric,
          planningValue,
          referenceValue,
          null,
          ReconciliationStatus.NOT_AVAILABLE,
          ReconciliationQuality.UNAVAILABLE,
          sourceDescription(metric),
          tolerance);
    BigDecimal difference = planningValue.subtract(referenceValue);
    return new PlanningMetricReconciliation(
        metric,
        planningValue,
        referenceValue,
        difference,
        difference.abs().compareTo(tolerance) <= 0
            ? ReconciliationStatus.MATCHED
            : ReconciliationStatus.DIFFERENT,
        ReconciliationQuality.EXACT,
        sourceDescription(metric),
        tolerance);
  }

  private Map<PlanningMetric, PlanningMetricValue> accountingValues(Long portfolioId, int year) {
    HistoricalPortfolioYear source = historicalPortfolio.read(portfolioId, year);
    if (!source.complete()) return Map.of();
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    result.put(
        PlanningMetric.MARKET_ASSETS,
        value(PlanningMetric.MARKET_ASSETS, source.endMarketAssets()));
    result.put(
        PlanningMetric.MARKET_INCOME, value(PlanningMetric.MARKET_INCOME, source.marketIncome()));
    result.put(
        PlanningMetric.MARKET_WITHDRAWAL,
        value(PlanningMetric.MARKET_WITHDRAWAL, source.netWithdrawal()));
    if (source.marketReturn() != null)
      result.put(
          PlanningMetric.MARKET_RETURN, value(PlanningMetric.MARKET_RETURN, source.marketReturn()));
    return result;
  }

  private static String sourceDescription(PlanningMetric metric) {
    if (metric == PlanningMetric.MARKET_WITHDRAWAL)
      return "Net portfolio withdrawal = gross withdrawals − deposits";
    if (metric == PlanningMetric.MARKET_ASSETS)
      return "Historical portfolio-performance view · calendar year ending Dec 31";
    return "Historical portfolio-performance view · calendar year ending Dec 31";
  }

  private static PlanningMetricValue value(PlanningMetric metric, BigDecimal amount) {
    return new PlanningMetricValue(
        metric, amount, null, PlanningValueSource.ACCOUNTING_DERIVED, null);
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
