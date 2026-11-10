package com.smartbox.investory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class InvestmentMcpControllerTest {
  private final InvestmentMcpClient client = org.mockito.Mockito.mock(InvestmentMcpClient.class);
  private final InvestmentMcpController controller =
      new InvestmentMcpController(client, new tools.jackson.databind.ObjectMapper());

  @Test
  void advertisesOnlyReadOnlyTools() {
    ResponseEntity<?> response =
        controller.handle(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"));
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().toString().contains("get_portfolio_dashboard"));
    assertTrue(response.getBody().toString().contains("get_investment_ytd"));
  }

  @Test
  void delegatesPerformanceToInjectedClient() {
    var expected =
        new InvestmentDashboardApi.PerformanceKpiView(true, null, "Available", "2026-01-01");
    when(client.performance(7)).thenReturn(expected);
    var response =
        controller.handle(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                2,
                "method",
                "tools/call",
                "params",
                Map.of(
                    "name", "get_portfolio_performance", "arguments", Map.of("portfolioId", 7))));
    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().toString().contains("structuredContent"));
    verify(client).performance(7);
  }

  @Test
  void rejectsInvalidPortfolioId() {
    var response =
        controller.handle(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                3,
                "method",
                "tools/call",
                "params",
                Map.of("name", "get_investment_ytd", "arguments", Map.of("portfolioId", 0))));
    assertTrue(response.getBody().toString().contains("-32602"));
    org.mockito.Mockito.verifyNoInteractions(client);
  }
}
