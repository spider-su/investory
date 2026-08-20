package com.smartbox.investory.services.portfolio.read;

import java.util.Optional;

/** Reads optional brokerage portfolio context for cross-domain validation and valuation. */
public interface BrokeragePortfolioContextReader {
  Optional<BrokeragePortfolioContext> findById(Long portfolioId);
}
