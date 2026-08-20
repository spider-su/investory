package com.smartbox.investory.investment.infrastructure.market.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Public Yahoo Finance chart endpoint fallback for a single current listing price. */
@Slf4j
@Service
public class YahooFinanceService {

  private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public Optional<YahooQuote> fetchLatestQuote(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return Optional.empty();
    }
    try {
      URI uri =
          URI.create(
              BASE_URL
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
      JsonNode result = MAPPER.readTree(response.body()).path("chart").path("result");
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
      log.warn("Yahoo Finance quote skipped for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Yahoo Finance quote interrupted for {}", symbol);
      return Optional.empty();
    } catch (RuntimeException e) {
      log.warn("Yahoo Finance quote skipped for {}: {}", symbol, e.getMessage());
      return Optional.empty();
    }
  }

  public record YahooQuote(String symbol, String currency, LocalDate date, double price) {}
}
