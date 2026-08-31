package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class PortfolioNotFoundException extends ResourceNotFoundException {
  public PortfolioNotFoundException(Long portfolioId) {
    super("Portfolio %s was not found".formatted(portfolioId));
  }
}
