package com.smartbox.investory.integrations.market.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  @DisplayName("keeps concurrent override, default quote, and history endpoints isolated")
  @Test
  void keepsConcurrentEndpointsIsolated() throws Exception {
    var overrideRequests = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var defaultRequests = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var overrideArrived = new CountDownLatch(1);
    var releaseOverride = new CountDownLatch(1);
    var overrideServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    overrideServer.createContext(
        "/custom/SPY",
        exchange -> {
          overrideRequests.add(exchange.getRequestURI().toString());
          overrideArrived.countDown();
          await(releaseOverride);
          respond(exchange, 200, quoteResponse("SPY", 500.0));
        });
    overrideServer.start();
    var historyFrom = LocalDate.of(2026, 9, 2);
    long period1 = historyFrom.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
    long period2 = historyFrom.plusDays(2).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
    server.createContext(
        "/v8/finance/chart/AAPL",
        exchange -> {
          defaultRequests.add(exchange.getRequestURI().toString());
          respond(exchange, 200, quoteResponse("AAPL", 200.0));
        });
    server.createContext(
        "/v8/finance/chart/MSFT",
        exchange -> {
          defaultRequests.add(exchange.getRequestURI().toString());
          respond(
              exchange,
              200,
              """
              {"chart":{"result":[{"timestamp":[1788307200],"indicators":{"quote":[{"close":[101.25]}]}}]}}
              """);
        });
    YahooFinanceService service = new YahooFinanceService();
    service.setBaseUrl(baseUrl + "/v8/finance/chart");
    String overrideUrl = "http://127.0.0.1:" + overrideServer.getAddress().getPort() + "/custom";
    try (var executor = Executors.newFixedThreadPool(3)) {
      Future<?> override = executor.submit(() -> service.fetchLatestQuote("SPY", overrideUrl));
      assertTrue(overrideArrived.await(5, TimeUnit.SECONDS));
      Future<?> quote = executor.submit(() -> service.fetchLatestQuote("AAPL"));
      Future<?> history =
          executor.submit(
              () -> service.fetchDailyCloses("MSFT", historyFrom, historyFrom.plusDays(1)));
      quote.get(5, TimeUnit.SECONDS);
      history.get(5, TimeUnit.SECONDS);
      releaseOverride.countDown();
      override.get(5, TimeUnit.SECONDS);
    } finally {
      releaseOverride.countDown();
      overrideServer.stop(0);
    }

    assertEquals(1, overrideRequests.size());
    assertEquals("/custom/SPY?range=5d&interval=1d", overrideRequests.getFirst());
    assertEquals(2, defaultRequests.size());
    assertTrue(defaultRequests.contains("/v8/finance/chart/AAPL?range=5d&interval=1d"));
    assertTrue(
        defaultRequests.contains(
            "/v8/finance/chart/MSFT?period1="
                + period1
                + "&period2="
                + period2
                + "&interval=1d&events=history"));
  }

  @DisplayName("keeps distinct concurrent overrides isolated and leaves default unchanged")
  @Test
  void keepsDistinctOverridesIsolated() throws Exception {
    var pathsA = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var pathsB = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var arrivedA = new CountDownLatch(1);
    var arrivedB = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var serverA = overrideServer("/one", pathsA, arrivedA, release);
    var serverB = overrideServer("/two", pathsB, arrivedB, release);
    YahooFinanceService service = new YahooFinanceService();
    service.setBaseUrl(baseUrl + "/v8/finance/chart");
    try {
      String urlA = "http://127.0.0.1:" + serverA.getAddress().getPort() + "/one";
      String urlB = "http://127.0.0.1:" + serverB.getAddress().getPort() + "/two";
      try (var executor = Executors.newFixedThreadPool(2)) {
        Future<?> requestA = executor.submit(() -> service.fetchLatestQuote("AAA", urlA));
        Future<?> requestB = executor.submit(() -> service.fetchLatestQuote("BBB", urlB));
        assertTrue(arrivedA.await(5, TimeUnit.SECONDS));
        assertTrue(arrivedB.await(5, TimeUnit.SECONDS));
        release.countDown();
        requestA.get(5, TimeUnit.SECONDS);
        requestB.get(5, TimeUnit.SECONDS);
      }
      assertEquals("/one/AAA?range=5d&interval=1d", pathsA.getFirst());
      assertEquals("/two/BBB?range=5d&interval=1d", pathsB.getFirst());
      server.createContext(
          "/v8/finance/chart/DEF", exchange -> respond(exchange, 200, quoteResponse("DEF", 12.0)));
      assertEquals(12.0, service.fetchLatestQuote("DEF").orElseThrow().price());
    } finally {
      release.countDown();
      serverA.stop(0);
      serverB.stop(0);
    }
  }

  private HttpServer overrideServer(
      String path, java.util.List<String> requests, CountDownLatch arrived, CountDownLatch release)
      throws IOException {
    HttpServer override = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    override.createContext(
        path,
        exchange -> {
          requests.add(exchange.getRequestURI().toString());
          arrived.countDown();
          await(release);
          respond(exchange, 200, quoteResponse("ignored", 10.0));
        });
    override.start();
    return override;
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("request timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static String quoteResponse(String currency, double price) {
    return "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\""
        + currency
        + "\",\"regularMarketPrice\":"
        + price
        + ",\"regularMarketTime\":1787529600}}]}}";
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
