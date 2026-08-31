package com.smartbox.investory.integrations.market.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Yahoo Finance Transport Contract")
class YahooFinanceTransportContractTest {
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

  @DisplayName("uses Chart Transport Contract And Parses Response")
  @Test
  void usesChartTransportContractAndParsesResponse() {
    AtomicReference<String> requestTarget = new AtomicReference<>();
    AtomicReference<String> userAgent = new AtomicReference<>();
    server.createContext(
        "/v8/finance/chart/BRK.B",
        exchange -> {
          requestTarget.set(exchange.getRequestURI().toString());
          userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
          respond(
              exchange,
              200,
              """
              {"chart":{"result":[{"meta":{"currency":"USD",\
              "regularMarketPrice":502.25,"regularMarketTime":1787529600}}],"error":null}}
              """);
        });
    YahooFinanceService service = new YahooFinanceService();
    service.setBaseUrl(baseUrl + "/v8/finance/chart");

    var quote = service.fetchLatestQuote("BRK.B").orElseThrow();

    assertEquals(502.25, quote.price(), 0.000001);
    assertEquals(LocalDate.of(2026, 8, 24), quote.date());
    assertEquals("/v8/finance/chart/BRK.B?range=5d&interval=1d", requestTarget.get());
    assertEquals("Investory/1.0", userAgent.get());
  }

  @DisplayName("returns Empty For Non Success Http Status")
  @Test
  void returnsEmptyForNonSuccessHttpStatus() {
    server.createContext("/v8/finance/chart/AAPL", exchange -> respond(exchange, 404, "missing"));
    YahooFinanceService service = new YahooFinanceService();
    service.setBaseUrl(baseUrl + "/v8/finance/chart");

    assertTrue(service.fetchLatestQuote("AAPL").isEmpty());
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
