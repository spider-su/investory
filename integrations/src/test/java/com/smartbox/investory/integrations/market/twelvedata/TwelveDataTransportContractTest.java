package com.smartbox.investory.integrations.market.twelvedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Twelve Data Transport Contract")
class TwelveDataTransportContractTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @DisplayName("twelve Data Uses Quote Transport Contract And Parses Response")
  @Test
  void twelveDataUsesQuoteTransportContractAndParsesResponse() {
    AtomicReference<String> requestTarget = new AtomicReference<>();
    server.createContext(
        "/quote",
        exchange -> {
          requestTarget.set(exchange.getRequestURI().toString());
          respond(
              exchange,
              200,
              """
              {"symbol":"AAPL","currency":"USD","datetime":"2026-08-24",\
              "open":"226.10","high":"229.00","low":"225.00","close":"227.50",\
              "volume":"1000","is_market_open":false}
              """);
        });
    TwelveDataService service = new TwelveDataService();
    service.setBaseUrl(baseUrl);
    service.setApiKey("contract key");

    var quotes = service.fetchMarketQuotes("AAPL");

    assertEquals(227.50, quotes.get("AAPL").getClose(), 0.000001);
    assertTrue(requestTarget.get().startsWith("/quote?"));
    assertTrue(requestTarget.get().contains("symbol=AAPL"));
    assertTrue(requestTarget.get().contains("apikey=contract+key"));
  }

  @DisplayName("twelve Data Maps Non Success Http Status")
  @Test
  void twelveDataMapsNonSuccessHttpStatus() {
    server.createContext("/quote", exchange -> respond(exchange, 429, "rate limited"));
    TwelveDataService service = new TwelveDataService();
    service.setBaseUrl(baseUrl);
    service.setApiKey("key");

    RuntimeException error =
        assertThrows(RuntimeException.class, () -> service.fetchMarketQuotes("AAPL"));

    assertTrue(error.getCause() instanceof TwelveDataException);
    assertTrue(error.getCause().getMessage().contains("HTTP 429"));
  }

  @DisplayName("twelve Data Uses Monthly Time Series Transport Contract")
  @Test
  void twelveDataUsesMonthlyTimeSeriesTransportContract() {
    AtomicReference<String> requestTarget = new AtomicReference<>();
    server.createContext(
        "/time_series",
        exchange -> {
          requestTarget.set(exchange.getRequestURI().toString());
          respond(
              exchange,
              200,
              """
              {"values":[{"datetime":"2026-08-01","close":"645.25"},
              {"datetime":"2026-07-01","close":"630.10"}]}
              """);
        });
    TwelveDataService service = new TwelveDataService();
    service.setBaseUrl(baseUrl);
    service.setApiKey("key");

    var closes = service.fetchMonthlyCloses("SPY", 24);

    assertEquals(Map.of("2026-07", 630.10, "2026-08", 645.25), closes);
    assertTrue(requestTarget.get().contains("symbol=SPY"));
    assertTrue(requestTarget.get().contains("interval=1month"));
    assertTrue(requestTarget.get().contains("outputsize=24"));
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().put("Content-Type", java.util.List.of("application/json"));
    exchange.sendResponseHeaders(status, bytes.length);
    try (var response = exchange.getResponseBody()) {
      response.write(bytes);
    }
  }
}
