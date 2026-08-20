package com.smartbox.investory.investment.api;

import java.util.Optional;

/** Reads optional brokerage portfolio context for cross-domain validation and valuation. */
public interface BrokeragePortfolioContextReader {
  Optional<BrokeragePortfolioContext> findById(Long portfolioId);
}
