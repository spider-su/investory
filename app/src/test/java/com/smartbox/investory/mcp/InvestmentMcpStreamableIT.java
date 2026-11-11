package com.smartbox.investory.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Connects a standard MCP client to the full application and snapshot-backed portfolio data. */
@SpringBootTest(
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
@ActiveProfiles("test-fast")
class InvestmentMcpStreamableIT {
  private static final String TEST_PASSWORD = "mcp-test-password";
  private static final WorkerDatabase DATABASE = FastDatabase.scopedDatabase("mcp_streamable");

  @LocalServerPort private int port;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService projectionRefresh;

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @BeforeEach
  void loadPortfolioSnapshot() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-common.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-broker.sql"));
      jdbc.update(
          "UPDATE investory.app_users SET password_hash = ? WHERE username = ?",
          passwordEncoder.encode(TEST_PASSWORD),
          "happy.investor");
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM investory.positions WHERE open_time::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement =
          connection.prepareStatement(
              "UPDATE investory.positions SET close_time = NULL WHERE close_time::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM investory.cash_operations WHERE date::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement = connection.createStatement()) {
        statement.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
      }
      seedCurrentValuationFx(connection);
    }
    projections.recalculateAccounts(
        Set.of(
            HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
            HappyInvestorTestData.XTB_EUR_ACCOUNT_ID));
    projectionRefresh.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.DASHBOARD);
  }

  @Test
  void officialMcpClientReadsPersistedPositionsAndRejectsUnauthenticatedRequests()
      throws Exception {
    var unauthenticated =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    var unauthenticatedResponse =
        HttpClient.newHttpClient().send(unauthenticated, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, unauthenticatedResponse.statusCode());

    String credentials =
        Base64.getEncoder()
            .encodeToString(("happy.investor:" + TEST_PASSWORD).getBytes(StandardCharsets.UTF_8));
    var transport =
        HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
            .endpoint("/mcp")
            .httpRequestCustomizer(
                (request, method, uri, body, context) ->
                    request.header("Authorization", "Basic " + credentials))
            .build();
    try (McpSyncClient client =
        McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation("investory-persisted-test", "1.0"))
            .build()) {
      client.initialize();
      assertThat(client.listTools().tools())
          .extracting(McpSchema.Tool::name)
          .containsExactlyInAnyOrder(
              "get_portfolio_dashboard", "get_portfolio_performance", "get_investment_ytd");

      var result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "get_portfolio_dashboard",
                  Map.of("portfolioId", HappyInvestorTestData.PORTFOLIO_ID)));
      assertFalse(Boolean.TRUE.equals(result.isError()));
      assertThat(result.structuredContent()).isInstanceOf(Map.class);
      assertThat(result.content().toString()).contains("NVDA.US", "TSLA.US");

      var crossProfile =
          client.callTool(
              new McpSchema.CallToolRequest("get_investment_ytd", Map.of("portfolioId", 999)));
      assertTrue(Boolean.TRUE.equals(crossProfile.isError()));
      assertThat(crossProfile.content().toString()).contains("Portfolio access denied");
    }
  }

  private static void seedCurrentValuationFx(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
            INSERT INTO investory.exchange_rates(
                rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
            WITH anchors AS (
              SELECT
                (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'USD' AND to_currency = 'PLN' ORDER BY id DESC LIMIT 1) AS usd_pln,
                (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'EUR' AND to_currency = 'USD' ORDER BY id DESC LIMIT 1) AS eur_usd
            ), matrix(source_currency, target_currency, rate) AS (
              SELECT 'USD', 'PLN', usd_pln FROM anchors
              UNION ALL SELECT 'PLN', 'USD', 1 / usd_pln FROM anchors
              UNION ALL SELECT 'EUR', 'USD', eur_usd FROM anchors
              UNION ALL SELECT 'USD', 'EUR', 1 / eur_usd FROM anchors
              UNION ALL SELECT 'EUR', 'PLN', eur_usd * usd_pln FROM anchors
              UNION ALL SELECT 'PLN', 'EUR', 1 / (eur_usd * usd_pln) FROM anchors
            )
            SELECT CURRENT_DATE, source_currency, target_currency, rate,
                   'TEST', 'OBSERVED', ?, 'HAPPYINVESTOR_REFERENCE'
            FROM matrix
            ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO UPDATE
              SET rate = EXCLUDED.rate,
                  source = EXCLUDED.source,
                  method = EXCLUDED.method,
                  source_rate_date = EXCLUDED.source_rate_date,
                  source_reference = EXCLUDED.source_reference
            """)) {
      statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
      statement.setObject(2, HappyInvestorTestData.REFERENCE_DATE);
      statement.setObject(3, HappyInvestorTestData.REFERENCE_DATE);
      statement.executeUpdate();
    }
  }
}
