package com.smartbox.investory.investment.api;

import com.smartbox.investory.services.PortfolioService;
import com.smartbox.investory.services.models.Portfolio;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application read boundary over the current brokerage reporting aggregation. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BrokeragePortfolioReadService {
  private final PortfolioService portfolioService;

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

  /** Compatibility read for callers that still require the legacy reporting model. */
  public Portfolio currentMarketInvestments() {
    return portfolioService.calculateTotalProfitLoss();
  }

  private static BigDecimal money(double value) {
    return BigDecimal.valueOf(value);
  }
}
