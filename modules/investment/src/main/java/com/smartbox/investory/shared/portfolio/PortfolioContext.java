package com.smartbox.investory.shared.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;

/** Stable portfolio identity and base-currency context shared between domain boundaries. */
public record PortfolioContext(Long portfolioId, CurrencyType baseCurrency) {}
