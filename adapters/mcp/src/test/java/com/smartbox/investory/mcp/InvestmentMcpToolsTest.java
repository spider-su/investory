package com.smartbox.investory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class InvestmentMcpToolsTest {
  private final InvestmentMcpClient client = org.mockito.Mockito.mock(InvestmentMcpClient.class);
  private final InvestmentMcpAuthorization authorization =
      org.mockito.Mockito.mock(InvestmentMcpAuthorization.class);
  private final InvestmentMcpTools tools = new InvestmentMcpTools(client, authorization);
  private final TestingAuthenticationToken user =
      new TestingAuthenticationToken("profile-user", "secret", "ROLE_PROFILE_USER");

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsRealApiDtoThroughReplaceableClient() {
    SecurityContextHolder.getContext().setAuthentication(user);
    var expected =
        new InvestmentDashboardApi.PerformanceKpiView(true, null, "Available", "2026-01-01");
    when(authorization.canReadPortfolio(7, user)).thenReturn(true);
    when(client.performance(7)).thenReturn(expected);

    assertEquals(expected, tools.getPortfolioPerformance(7));

    verify(authorization).canReadPortfolio(7, user);
    verify(client).performance(7);
  }

  @Test
  void deniesPortfolioOutsideAuthenticatedProfile() {
    SecurityContextHolder.getContext().setAuthentication(user);
    when(authorization.canReadPortfolio(8, user)).thenReturn(false);

    assertThrows(AccessDeniedException.class, () -> tools.getInvestmentYtd(8));

    org.mockito.Mockito.verifyNoInteractions(client);
  }

  @Test
  void rejectsInvalidPortfolioBeforeAuthorizationOrDataAccess() {
    assertThrows(IllegalArgumentException.class, () -> tools.getInvestmentYtd(0));

    org.mockito.Mockito.verifyNoInteractions(client, authorization);
  }
}
