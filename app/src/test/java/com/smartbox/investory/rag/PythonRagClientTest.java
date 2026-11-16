package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class PythonRagClientTest {
  private HttpServer server;
  private PythonRagClient client;
  private ProfileResponse snapshot;
  private final AtomicReference<String> request = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> response =
      new AtomicReference<>(
          "{\"answer\":\"123.45 PLN [live:profile]\",\"sources\":[\"live:profile\"]}");
  private final AtomicInteger status = new AtomicInteger(200);

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ask-with-facts",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/ask",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/search",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/market-data",
        exchange -> {
          byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    client =
        new PythonRagClient(
            RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "internal-test-token");
    snapshot = mock(ProfileResponse.class);
    when(snapshot.currency()).thenReturn(CurrencyType.PLN);
    when(snapshot.totalNetWorth()).thenReturn(new BigDecimal("123.45"));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void documentationSearchSendsOnlyBoundedPublicQuery() throws Exception {
    response.set(
        "{\"matches\":[{\"source\":\"accounting.md\",\"text\":\"Contribution is external flow.\",\"score\":0.8}]}");
    var result = client.searchDocumentation("Explain contributions", 2);
    assertEquals(1, result.matches().size());
    var payload = new ObjectMapper().readTree(request.get());
    assertEquals(2, payload.size());
    assertEquals("Explain contributions", payload.path("question").asString());
    assertEquals(2, payload.path("limit").asInt());
    assertEquals("Bearer internal-test-token", authorization.get());
  }

  @Test
  void providerQuotaHasTypedSanitizedFailure() {
    status.set(429);
    response.set("sensitive diagnostic");
    assertThrows(
        PythonRagClient.MarketProviderRateLimitedException.class,
        () -> client.marketData("GLOBAL_QUOTE", "MSFT"));
  }

  @Test
  void unavailableMarketConfigurationIsDistinctFromUpstreamFailure() {
    response.set("sensitive provider diagnostic must not escape");
    status.set(503);
    var unavailable =
        assertThrows(
            PythonRagClient.MarketResearchUnavailableException.class,
            () -> client.marketData("GLOBAL_QUOTE", "MSFT"));
    assertNull(unavailable.getMessage());
    assertNull(unavailable.getCause());
    status.set(424);
    assertThrows(
        PythonRagClient.MarketDataNotEntitledException.class,
        () -> client.marketData("GLOBAL_QUOTE", "MSFT"));
    status.set(502);
    assertThrows(
        PythonRagClient.RagUnavailableException.class,
        () -> client.marketData("GLOBAL_QUOTE", "MSFT"));
  }

  @Test
  void sendsOnlyAllowlistedFactsUsingInternalCredentials() {
    var result = client.ask("What is my net worth?", snapshot);
    assertEquals("123.45 PLN [live:profile]", result.answer());
    assertEquals("Bearer internal-test-token", authorization.get());
    var payload = new ObjectMapper().readTree(request.get());
    assertEquals(Set.of("question", "facts"), Set.copyOf(payload.propertyNames()));
    var facts = payload.get("facts");
    assertEquals(
        Set.of(
            "currency",
            "marketPortfolioValue",
            "longTermAssetValue",
            "totalNetWorth",
            "liquidAssets",
            "illiquidAssets"),
        Set.copyOf(facts.propertyNames()));
    assertEquals("PLN", facts.get("currency").asText());
    assertEquals(new BigDecimal("123.45"), facts.get("totalNetWorth").decimalValue());
    assertTrue(facts.get("illiquidAssets").isNull());
  }

  @Test
  void documentationRequestDoesNotForwardFinancialSnapshotOrPortfolioId() {
    response.set(
        "{\"answer\":\"Transfer accounting is documented [transfers.md]\",\"sources\":[\"transfers.md\"]}");
    var answer = client.askDocumentation("How are transfers handled?");
    assertEquals("Transfer accounting is documented [transfers.md]", answer.answer());
    assertEquals("Bearer internal-test-token", authorization.get());
    var payload = new ObjectMapper().readTree(request.get());
    assertEquals(Set.of("question"), Set.copyOf(payload.propertyNames()));
    assertEquals("How are transfers handled?", payload.get("question").asText());
  }

  @Test
  void rejectsEmptyMalformedAndIncompleteUpstreamResponses() {
    for (String body :
        new String[] {
          "",
          "not-json",
          "{}",
          "{\"answer\":\"\",\"sources\":[]}",
          "{\"answer\":\"answer\",\"sources\":[null]}"
        }) {
      response.set(body);
      assertThrows(
          PythonRagClient.RagUnavailableException.class, () -> client.ask("Question", snapshot));
    }
  }

  @Test
  void sanitizesUpstreamFailure() {
    status.set(503);
    response.set("private upstream diagnostics");
    var error =
        assertThrows(
            PythonRagClient.RagUnavailableException.class, () -> client.ask("Question", snapshot));
    assertNull(error.getMessage());
    assertNull(error.getCause());
  }

  @Test
  void failsClosedWithoutServiceToken() {
    assertThrows(
        IllegalStateException.class,
        () -> new PythonRagClient(RestClient.builder(), "http://127.0.0.1", ""));
  }
}
