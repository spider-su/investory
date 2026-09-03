package com.smartbox.investory.shared.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;

/** Stable portfolio identity and currency context shared between domain boundaries. */
public record PortfolioContext(
    Long portfolioId, CurrencyType baseCurrency, CurrencyType localCurrency) {
  /** Compatibility constructor for callers that only provide accounting context. */
  public PortfolioContext(Long portfolioId, CurrencyType baseCurrency) {
    this(portfolioId, baseCurrency, CurrencyType.PLN);
  }
}
