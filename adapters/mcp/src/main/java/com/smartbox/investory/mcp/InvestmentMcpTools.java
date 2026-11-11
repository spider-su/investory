package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Read-only MCP tools backed by Investment's public dashboard API. */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class InvestmentMcpTools {
  private final InvestmentMcpClient investment;
  private final InvestmentMcpAuthorization authorization;

  public InvestmentMcpTools(
      InvestmentMcpClient investment, InvestmentMcpAuthorization authorization) {
    this.investment = investment;
    this.authorization = authorization;
  }

  public InvestmentDashboardApi.DashboardPageView getPortfolioDashboard(long portfolioId) {
    return invoke(portfolioId, () -> investment.dashboard(portfolioId));
  }

  public InvestmentDashboardApi.PerformanceKpiView getPortfolioPerformance(long portfolioId) {
    return invoke(portfolioId, () -> investment.performance(portfolioId));
  }

  public InvestmentDashboardApi.InvestmentResultView getInvestmentYtd(long portfolioId) {
    return invoke(portfolioId, () -> investment.yearToDate(portfolioId));
  }

  private <T> T invoke(long portfolioId, Supplier<T> query) {
    if (portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current == null || !authorization.canReadPortfolio(portfolioId, current)) {
      throw new AccessDeniedException("Portfolio access denied");
    }
    try {
      return query.get();
    } catch (AccessDeniedException | IllegalArgumentException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Portfolio data is unavailable");
    }
  }
}
