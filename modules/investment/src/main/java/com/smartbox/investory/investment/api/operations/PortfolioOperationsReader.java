package com.smartbox.investory.investment.api.operations;

import java.math.BigDecimal;

/** Current portfolio totals used by operational adapters such as notifications and health jobs. */
public interface PortfolioOperationsReader {
  PortfolioOperationsSnapshot portfolio();

  record PortfolioOperationsSnapshot(
      String baseCurrency,
      BigDecimal balance,
      BigDecimal totalProfit,
      BigDecimal unrealizedProfit,
      BigDecimal realizedProfit,
      BigDecimal dividends,
      BigDecimal capitalGainsTax) {}
}
