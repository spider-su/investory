package com.smartbox.investory.shared.portfolio;

import java.util.Optional;

/** Reads optional portfolio context needed by cross-domain validation and valuation. */
public interface PortfolioContextReader {
  Optional<PortfolioContext> findById(Long portfolioId);
}
