package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import java.math.BigDecimal;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application read boundary over the current brokerage reporting aggregation. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BrokeragePortfolioReadService implements BrokeragePortfolioReader {
  private final PortfolioMetricsService portfolioMetricsService;
  private final PortfolioPerformanceQuery performanceQuery;
  private final PortfolioKpiSummaryRepository portfolioKpis;
  private final PortfolioAssetAllocationRepository portfolioAllocations;

  public SharedBrokeragePortfolioSnapshot currentSharedSnapshot() {
    Portfolio portfolio = portfolioMetricsService.calculateTotalProfitLoss();
    return new SharedBrokeragePortfolioSnapshot(
        portfolio.getBaseCurrency(),
        money(portfolio.getBalance()),
        money(portfolio.getCash()),
        money(portfolio.getDividends()),
        money(portfolio.getInterest()),
        portfolio.getOpenPositionValues() == null
            ? java.util.List.of()
            : portfolio.getOpenPositionValues().stream()
                .map(
                    position ->
                        new BrokeragePositionSnapshot(
                            position.getSymbol(), money(position.getValue())))
                .toList());
  }

  @Override
  public SharedBrokeragePortfolioSnapshot currentSnapshot(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    PortfolioKpiSummaryEntity kpi =
        portfolioKpis
            .findById(portfolioId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown portfolio: " + portfolioId));
    java.util.List<BrokeragePositionSnapshot> positions =
        portfolioAllocations.findAllByPortfolioId(portfolioId).stream()
            .map(
                allocation ->
                    new BrokeragePositionSnapshot(
                        allocation.getAssetSymbol(),
                        money(allocation.getTotalValueInBaseCurrency())))
            .toList();
    return new SharedBrokeragePortfolioSnapshot(
        kpi.getBaseCurrency(),
        money(kpi.getTotalEquity()),
        money(kpi.getTotalCash()),
        money(kpi.getTotalDividends()),
        money(kpi.getTotalInterest()),
        positions);
  }

  @Override
  public BrokerageIncomeSnapshot incomeForMonths(YearMonth from, YearMonth to) {
    PerformanceResult result = performanceQuery.forMonths(from, to);
    return toIncomeSnapshot(result);
  }

  @Override
  public BrokerageIncomeSnapshot incomeForMonths(Long portfolioId, YearMonth from, YearMonth to) {
    PerformanceResult result = performanceQuery.forPortfolioMonths(portfolioId, from, to);
    return toIncomeSnapshot(result);
  }

  private BrokerageIncomeSnapshot toIncomeSnapshot(PerformanceResult result) {
    return new BrokerageIncomeSnapshot(
        result.baseCurrency(),
        result.period().startDate(),
        result.period().endDate(),
        result.startValue(),
        result.endValue(),
        result.dividends(),
        result.interest(),
        result.taxes());
  }

  private static BigDecimal money(double value) {
    return BigDecimal.valueOf(value);
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
