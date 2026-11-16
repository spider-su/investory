package com.smartbox.investory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest(
    classes = InvestmentMcpEndToEndTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.mcp.enabled=true",
      "spring.ai.mcp.server.enabled=true",
      "spring.ai.mcp.server.protocol=STREAMABLE",
      "spring.ai.mcp.server.name=investory",
      "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
      "spring.ai.mcp.server.capabilities.resource=false",
      "spring.ai.mcp.server.capabilities.prompt=false",
      "spring.ai.mcp.server.capabilities.completion=false",
      "spring.ai.mcp.server.annotation-scanner.enabled=false"
    })
class InvestmentMcpEndToEndTest {
  @LocalServerPort private int port;

  @Test
  void officialSdkClientInitializesListsToolsAndInvokesPortfolioTool() throws Exception {
    var unauthenticatedRequest =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                        + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                        + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}"))
            .build();
    HttpResponse<String> unauthenticatedResponse =
        HttpClient.newHttpClient()
            .send(unauthenticatedRequest, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, unauthenticatedResponse.statusCode());

    String credentials =
        Base64.getEncoder().encodeToString("owner:secret".getBytes(StandardCharsets.UTF_8));
    var transport =
        HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
            .endpoint("/mcp")
            .httpRequestCustomizer(
                (request, method, uri, body, context) ->
                    request.header("Authorization", "Basic " + credentials))
            .build();
    try (McpSyncClient client =
        McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation("investory-test", "1.0"))
            .build()) {
      client.initialize();
      assertTrue(client.isInitialized());
      List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
      assertEquals(
          List.of(
              "get_benchmark_comparison",
              "get_investment_period_result",
              "get_investment_ytd",
              "get_investory_capabilities",
              "get_market_quote",
              "get_portfolio_dashboard",
              "get_portfolio_holdings",
              "get_portfolio_performance",
              "get_profile_summary",
              "get_retirement_plan",
              "search_investory_docs"),
          names.stream().sorted().toList());

      var result =
          client.callTool(
              new McpSchema.CallToolRequest("get_portfolio_performance", Map.of("portfolioId", 7)));
      assertFalse(Boolean.TRUE.equals(result.isError()));
      assertNotNull(result.structuredContent());
      assertTrue(result.content().toString().contains("Available"));
      assertTrue(result.content().toString().contains("2026-01-01"));

      var denied =
          client.callTool(
              new McpSchema.CallToolRequest("get_investment_ytd", Map.of("portfolioId", 8)));
      assertTrue(Boolean.TRUE.equals(denied.isError()));
      assertTrue(denied.content().toString().contains("Portfolio access denied"));

      var invalid =
          client.callTool(
              new McpSchema.CallToolRequest("get_investment_ytd", Map.of("portfolioId", 0)));
      assertTrue(Boolean.TRUE.equals(invalid.isError()));

      for (Number malformed :
          List.<Number>of(
              new java.math.BigInteger("9223372036854775808"),
              new java.math.BigDecimal("7.5"),
              new java.math.BigInteger("18446744073709551623"))) {
        var rejected =
            client.callTool(
                new McpSchema.CallToolRequest(
                    "get_portfolio_performance", Map.of("portfolioId", malformed)));
        assertTrue(Boolean.TRUE.equals(rejected.isError()));
        if (malformed instanceof java.math.BigInteger) {
          assertTrue(
              rejected.content().toString().contains("portfolioId must be a positive integer"));
        }
      }

      for (String name :
          List.of(
              "get_profile_summary",
              "get_portfolio_holdings",
              "get_investment_period_result",
              "get_benchmark_comparison",
              "get_retirement_plan",
              "search_investory_docs",
              "get_market_quote",
              "get_investory_capabilities")) {
        var arguments = new java.util.HashMap<String, Object>();
        arguments.put("portfolioId", 7);
        if (name.equals("search_investory_docs")) arguments.put("query", "How do transfers work?");
        if (name.equals("get_market_quote")) arguments.put("symbol", "MSFT");
        var success = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertFalse(Boolean.TRUE.equals(success.isError()), name);
        assertNotNull(success.structuredContent());
        arguments.put("portfolioId", 8);
        var forbidden = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertTrue(Boolean.TRUE.equals(forbidden.isError()), name);
        assertTrue(forbidden.content().toString().contains("Portfolio access denied"));
      }
      var max =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_investment_period_result", Map.of("portfolioId", 7, "period", "MAX")));
      assertFalse(Boolean.TRUE.equals(max.isError()));
      assertTrue(max.content().toString().contains("MAX"));
      var unsupportedPeriod =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_investment_period_result", Map.of("portfolioId", 7, "period", "2025")));
      assertTrue(Boolean.TRUE.equals(unsupportedPeriod.isError()));
      var oversizedHoldings =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_portfolio_holdings", Map.of("portfolioId", 7, "limit", 21)));
      assertTrue(Boolean.TRUE.equals(oversizedHoldings.isError()));

      assertThrows(
          McpError.class,
          () ->
              client.callTool(new McpSchema.CallToolRequest("unknown_investment_tool", Map.of())));
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
  @Import({InvestmentMcpConfiguration.class, InvestmentMcpTools.class, TestBeans.class})
  static class TestApplication {}

  @TestConfiguration
  static class TestBeans {
    @Bean
    @Primary
    InvestmentMcpClient testInvestmentMcpClient() {
      return new InvestmentMcpClient() {
        @Override
        public InvestmentDashboardApi.DashboardPageView dashboard(long portfolioId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public InvestmentDashboardApi.PerformanceKpiView performance(long portfolioId) {
          return new InvestmentDashboardApi.PerformanceKpiView(
              true, null, "Available", "2026-01-01");
        }

        @Override
        public InvestmentDashboardApi.InvestmentResultView yearToDate(long portfolioId) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Bean
    PortfolioResearchClient portfolioResearchClient() {
      var client = org.mockito.Mockito.mock(PortfolioResearchClient.class);
      var now = java.time.Instant.parse("2026-11-16T00:00:00Z");
      org.mockito.Mockito.when(client.profile(7L))
          .thenReturn(
              new PortfolioResearchClient.ProfileSummary(
                  "PLN", "123.45", "100", "23.45", "100", "23.45", now, "live:profile"));
      org.mockito.Mockito.when(client.holdings(7L, 5))
          .thenReturn(
              new PortfolioResearchClient.HoldingsSummary(
                  "PLN",
                  List.of(new PortfolioResearchClient.Holding("MSFT", "100", "10")),
                  now,
                  "live:investment-structure",
                  "current"));
      for (var period : com.smartbox.investory.investment.api.reporting.DashboardPeriod.values()) {
        org.mockito.Mockito.when(client.performance(7L, period))
            .thenReturn(
                new PortfolioResearchClient.PeriodResult(
                    period.wireValue(), "PLN", "5.00", true, now, "live:investment-performance"));
        org.mockito.Mockito.when(client.benchmark(7L, period))
            .thenReturn(
                new PortfolioResearchClient.BenchmarkResult(
                    period.wireValue(),
                    "PLN",
                    "SPY",
                    true,
                    "5",
                    "4",
                    "5",
                    "4",
                    now,
                    "live:investment-benchmark",
                    "existing scenario"));
      }
      org.mockito.Mockito.when(client.retirement(7L))
          .thenReturn(
              new PortfolioResearchClient.RetirementSummary(
                  false, null, null, null, null, List.of(), now, "live:retirement-plan"));
      org.mockito.Mockito.when(client.search(7L, "How do transfers work?", 4))
          .thenReturn(
              new PortfolioResearchClient.Documentation(
                  List.of(
                      new PortfolioResearchClient.Snippet(
                          "accounting.md", "Internal transfers conserve capital.", 0.9)),
                  now));
      org.mockito.Mockito.when(client.quote(7L, "MSFT"))
          .thenReturn(
              new PortfolioResearchClient.MarketQuote(
                  "Alpha Vantage",
                  "GLOBAL_QUOTE",
                  "MSFT",
                  "https://www.alphavantage.co/",
                  "public data",
                  "CACHED",
                  now,
                  java.time.LocalDate.of(2026, 11, 13),
                  now.plusSeconds(3600),
                  19,
                  List.of("EOD")));
      org.mockito.Mockito.when(client.capabilities(7L))
          .thenReturn(
              new PortfolioResearchClient.Capabilities(
                  List.of("YTD", "MAX"), true, 20, 19, "rolling 24 hours", "EOD"));
      return client;
    }

    @Bean
    InvestmentDashboardApi investmentDashboardApi() {
      return org.mockito.Mockito.mock(InvestmentDashboardApi.class);
    }

    @Bean
    InvestmentMcpAuthorization investmentMcpAuthorization() {
      return (portfolioId, authentication) ->
          portfolioId == 7 && "owner".equals(authentication.getName());
    }

    @Bean
    UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager(
          User.withUsername("owner").password("{noop}secret").roles("PROFILE_OWNER").build(),
          User.withUsername("other").password("{noop}secret").roles("USER").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("PROFILE_OWNER", "ADMIN"))
          .httpBasic(Customizer.withDefaults())
          .build();
    }
  }
}
