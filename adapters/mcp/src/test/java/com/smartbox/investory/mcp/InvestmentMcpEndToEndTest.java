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
          List.of("get_investment_ytd", "get_portfolio_dashboard", "get_portfolio_performance"),
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
