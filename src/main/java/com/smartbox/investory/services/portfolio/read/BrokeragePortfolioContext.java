package com.smartbox.investory.services.portfolio.read;

import com.smartbox.investory.shared.currency.CurrencyType;

/** Stable brokerage portfolio identity and base-currency context. */
public record BrokeragePortfolioContext(Long portfolioId, CurrencyType baseCurrency) {}
