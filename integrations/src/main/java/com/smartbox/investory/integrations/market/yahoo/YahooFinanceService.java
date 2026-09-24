package com.smartbox.investory.integrations.market.yahoo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Public Yahoo Finance chart endpoint adapter for quotes and price history. */
@Slf4j
@Service
public class YahooFinanceService {

  private static final String DEFAULT_BASE_URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final ObjectMapper objectMapper;

  private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  private String baseUrl = DEFAULT_BASE_URL;

  public YahooFinanceService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Test seam retaining construction without a Spring context. */
  YahooFinanceService() {
    this(new ObjectMapper());
  }

  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /** Test seam for transport-level tests against a loopback HTTP server. */
  void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
  }

  public Optional<YahooQuote> fetchLatestQuote(String symbol) {
    return fetchLatestQuote(symbol, baseUrl);
  }

  /** Fetches using a request-specific endpoint without changing the runtime default. */
  public Optional<YahooQuote> fetchLatestQuote(String symbol, String requestBaseUrl) {
    if (!StringUtils.hasText(symbol)) {
      return Optional.empty();
    }
    String previousBaseUrl = baseUrl;
    if (StringUtils.hasText(requestBaseUrl)) {
      baseUrl = requestBaseUrl.endsWith("/") ? requestBaseUrl : requestBaseUrl + "/";
    }
    try {
      URI uri =
          URI.create(
              baseUrl
                  + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                  + "?range=5d&interval=1d");
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(TIMEOUT)
              .header("User-Agent", "Investory/1.0")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("Yahoo Finance quote skipped for {}: HTTP {}", symbol, response.statusCode());
        return Optional.empty();
      }
      JsonNode result = objectMapper.readTree(response.body()).path("chart").path("result");
      if (!result.isArray() || result.isEmpty()) {
        log.warn("Yahoo Finance quote skipped for {}: no result", symbol);
        return Optional.empty();
      }
      JsonNode meta = result.get(0).path("meta");
      double price = meta.path("regularMarketPrice").asDouble(0.0);
      if (!Double.isFinite(price) || price <= 0.0) {
        log.warn("Yahoo Finance quote skipped for {}: no positive market price", symbol);
        return Optional.empty();
      }
      long marketTime = meta.path("regularMarketTime").asLong(0L);
      LocalDate date =
          marketTime > 0L
              ? Instant.ofEpochSecond(marketTime).atZone(ZoneOffset.UTC).toLocalDate()
              : LocalDate.now(ZoneOffset.UTC);
      String currency = meta.path("currency").asText(null);
      return Optional.of(new YahooQuote(symbol, currency, date, price));
    } catch (IOException e) {
      throw new IllegalStateException("Yahoo Finance request failed for " + symbol, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Yahoo Finance request interrupted for " + symbol, e);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Yahoo Finance response failed for " + symbol, e);
    } finally {
      baseUrl = previousBaseUrl;
    }
  }

  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to) {
    NavigableMap<LocalDate, Double> closes = new TreeMap<>();
    if (!StringUtils.hasText(symbol) || from == null || to == null || from.isAfter(to)) {
      return closes;
    }
    JsonNode result = fetchChart(symbol, from, to);
    JsonNode timestamps = result.path("timestamp");
    JsonNode closeValues = result.path("indicators").path("quote").path(0).path("close");
    if (!timestamps.isArray() || !closeValues.isArray()) return closes;
    for (int i = 0; i < Math.min(timestamps.size(), closeValues.size()); i++) {
      JsonNode close = closeValues.get(i);
      if (close == null || close.isNull()) continue;
      LocalDate date =
          Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneOffset.UTC).toLocalDate();
      double value = close.asDouble(0.0);
      if (!date.isBefore(from) && !date.isAfter(to) && Double.isFinite(value) && value > 0.0) {
        closes.put(date, value);
      }
    }
    return closes;
  }

  public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
    NavigableMap<String, Double> closes = new TreeMap<>();
    if (months <= 0) return closes;
    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = to.minusMonths(months).withDayOfMonth(1);
    for (var entry : fetchDailyCloses(symbol, from, to).entrySet()) {
      closes.put(YearMonth.from(entry.getKey()).toString(), entry.getValue());
    }
    return closes;
  }

  private JsonNode fetchChart(String symbol, LocalDate from, LocalDate to) {
    try {
      long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
      long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
      URI uri =
          URI.create(
              baseUrl
                  + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                  + "?period1="
                  + period1
                  + "&period2="
                  + period2
                  + "&interval=1d&events=history");
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(uri)
                  .timeout(TIMEOUT)
                  .header("User-Agent", "Investory/1.0")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) return objectMapper.createObjectNode();
      JsonNode result = objectMapper.readTree(response.body()).path("chart").path("result");
      return result.isArray() && !result.isEmpty()
          ? result.get(0)
          : objectMapper.createObjectNode();
    } catch (IOException e) {
      throw new IllegalStateException("Yahoo Finance history request failed for " + symbol, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Yahoo Finance history request interrupted for " + symbol, e);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Yahoo Finance history response failed for " + symbol, e);
    }
  }

  public record YahooQuote(String symbol, String currency, LocalDate date, double price) {}
}
