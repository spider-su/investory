package com.smartbox.investory.mcp;

import org.springframework.security.core.Authentication;

/** Application supplied profile access check for portfolio-scoped MCP calls. */
public interface InvestmentMcpAuthorization {
  boolean canReadPortfolio(long portfolioId, Authentication authentication);
}
