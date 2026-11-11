package com.smartbox.investory.config;

import com.smartbox.investory.mcp.InvestmentMcpAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Bridges MCP portfolio checks to the application's profile membership policy. */
@Component
public class InvestmentMcpAuthorizationAdapter implements InvestmentMcpAuthorization {
  private final AuthorizationService authorization;

  public InvestmentMcpAuthorizationAdapter(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  @Override
  public boolean canReadPortfolio(long portfolioId, Authentication authentication) {
    return authorization.canReadScoped(portfolioId, authentication);
  }
}
