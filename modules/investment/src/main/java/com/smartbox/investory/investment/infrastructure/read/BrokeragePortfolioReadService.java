package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.performance.PortfolioService;
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
  private final PortfolioService portfolioService;
  private final PortfolioPerformanceQuery performanceQuery;

  public SharedBrokeragePortfolioSnapshot currentSharedSnapshot() {
    Portfolio portfolio = portfolioService.calculateTotalProfitLoss();
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
  public BrokerageIncomeSnapshot incomeForMonths(YearMonth from, YearMonth to) {
    PerformanceResult result = performanceQuery.forMonths(from, to);
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
}
