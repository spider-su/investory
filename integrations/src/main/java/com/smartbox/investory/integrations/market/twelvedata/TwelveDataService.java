package com.smartbox.investory.integrations.market.twelvedata;

import com.smartbox.investory.investment.port.market.MarketQuote;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Native HTTP client for the TwelveData REST API.
 *
 * <p>Endpoints used: {@code /quote}, {@code /macd}, {@code /rsi}, {@code /time_series}. The API key
 * is sourced from {@code app.api.twelve-data-key}.
 *
 * <p>All requests go through the same {@link HttpClient} (reused) with a 10s timeout. Tests can
 * inject a stubbed {@code HttpClient} via the package-private constructor.
 */
@Slf4j
@Service
public class TwelveDataService {

  private static final String DEFAULT_BASE_URL = "https://api.twelvedata.com";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private final ObjectMapper objectMapper;

  private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  private String baseUrl = DEFAULT_BASE_URL;

  @Value("${app.api.twelve-data-key:}")
  private String apiKey;

  @Autowired
  public TwelveDataService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Test seam retaining construction without a Spring context. */
  TwelveDataService() {
    this.objectMapper = new ObjectMapper();

    // Default constructor; HttpClient is initialised inline and apiKey is
    // injected via @Value. Field injection (rather than constructor injection)
    // is used so the IntelliJ Spring inspector is satisfied — a constructor
    // taking only @Value-bound String is flagged as non-autowireable.
  }

  /** Test seam: swap the underlying HTTP client (e.g. for a Mockito mock). */
  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /** Test seam: set the API key without going through Spring property binding. */
  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  /** Test seam for transport-level tests against a loopback HTTP server. */
  void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  /**
   * Fetches monthly closing prices for a symbol, keyed by "yyyy-MM" (chronological). Used by {@code
   * BenchmarkService} for the SPY comparison curve. Returns an empty map on any error so the caller
   * can fall back to its previous-day cached values.
   */
  public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
    NavigableMap<String, Double> closes = new TreeMap<>();
    try {
      JsonNode json =
          get(
              "/time_series",
              Map.of("symbol", symbol, "interval", "1month", "outputsize", String.valueOf(months)));
      JsonNode values = json.path("values");
      if (values.isArray()) {
        for (JsonNode value : values) {
          String datetime = value.path("datetime").asText();
          if (datetime.length() >= 7) {
            closes.put(datetime.substring(0, 7), value.path("close").asDouble());
          }
        }
      }
    } catch (TwelveDataException e) {
      // Benchmark data is optional. TwelveData 429 must not look like an application
      // failure; BenchmarkService keeps the cached curve and retries on a later day.
      log.warn("Monthly benchmark fetch skipped for {}: {}", symbol, e.getMessage());
    }
    return closes;
  }

  public NavigableMap<String, Double> fetchMonthlyCloses(
      String symbol, int months, String requestBaseUrl) {
    String previous = baseUrl;
    try {
      if (requestBaseUrl != null && !requestBaseUrl.isBlank()) setBaseUrl(requestBaseUrl);
      return fetchMonthlyCloses(symbol, months);
    } finally {
      baseUrl = previous;
    }
  }

  /** Fetches observed daily closes for an inclusive date range. */
  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to) {
    return fetchDailyCloses(symbol, from, to, apiKey);
  }

  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to, String requestApiKey) {
    NavigableMap<LocalDate, Double> closes = new TreeMap<>();
    try {
      JsonNode json =
          get(
              "/time_series",
              Map.of(
                  "symbol",
                  symbol,
                  "interval",
                  "1day",
                  "start_date",
                  from.toString(),
                  "end_date",
                  to.toString(),
                  "order",
                  "ASC",
                  "outputsize",
                  "5000"),
              requestApiKey == null || requestApiKey.isBlank() ? apiKey : requestApiKey);
      JsonNode values = json.path("values");
      if (values.isArray()) {
        for (JsonNode value : values) {
          try {
            LocalDate date = LocalDate.parse(value.path("datetime").asText());
            double close = value.path("close").asDouble();
            if (!date.isBefore(from) && !date.isAfter(to) && close > 0.0) {
              closes.put(date, close);
            }
          } catch (DateTimeParseException ignored) {
            // Provider can return malformed rows; keep valid observations.
          }
        }
      }
    } catch (TwelveDataException e) {
      log.warn("Daily price history fetch skipped for {}: {}", symbol, e.getMessage());
    }
    return closes;
  }

  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to, String requestApiKey, String requestBaseUrl) {
    String previous = baseUrl;
    try {
      if (requestBaseUrl != null && !requestBaseUrl.isBlank()) setBaseUrl(requestBaseUrl);
      return fetchDailyCloses(symbol, from, to, requestApiKey);
    } finally {
      baseUrl = previous;
    }
  }

  /**
   * Fetches quote(s) by symbol. {@code joinedSymbols} is a comma-separated list of tickers.
   * Single-symbol responses come back as the quote at the JSON root; multi-symbol responses come
   * back as {@code {"AAPL": {...}, "MSFT": {...}}}.
   */
  public Map<String, MarketQuote> fetchMarketQuotes(String joinedSymbols) {
    return fetchMarketQuotes(joinedSymbols, apiKey);
  }

  public Map<String, MarketQuote> fetchMarketQuotes(String joinedSymbols, String requestApiKey) {
    Map<String, MarketQuote> result = new LinkedHashMap<>();
    JsonNode root;
    try {
      root = get("/quote", Map.of("symbol", joinedSymbols), requestApiKey);
    } catch (TwelveDataException e) {
      throw new RuntimeException("Failed to fetch quotes for " + joinedSymbols, e);
    }

    if (root.has("code") && root.path("code").asInt(200) != 200) {
      throw new IllegalArgumentException(
          "TwelveData /quote error: " + root.path("message").asText());
    }
    if (root.has("symbol")) {
      // Single-symbol response: the quote is at the root.
      MarketQuote single = parseQuote(root);
      if (single != null) {
        result.put(single.getSymbol(), single);
      }
      return result;
    }
    Iterator<String> fieldNames = root.propertyNames().iterator();
    while (fieldNames.hasNext()) {
      String symbol = fieldNames.next();
      MarketQuote q = parseQuote(root.path(symbol));
      if (q != null) {
        result.put(symbol, q);
      }
    }
    return result;
  }

  public Map<String, MarketQuote> fetchMarketQuotes(
      String joinedSymbols, String requestApiKey, String requestBaseUrl) {
    String previous = baseUrl;
    try {
      if (requestBaseUrl != null && !requestBaseUrl.isBlank()) setBaseUrl(requestBaseUrl);
      return fetchMarketQuotes(joinedSymbols, requestApiKey);
    } finally {
      baseUrl = previous;
    }
  }

  private MarketQuote parseQuote(JsonNode json) {
    if (json == null || json.isMissingNode() || json.has("code")) {
      return null;
    }
    try {
      MarketQuote quote = new MarketQuote();
      quote.setSymbol(json.path("symbol").asText(null));
      quote.setName(json.path("name").asText(null));
      quote.setExchange(json.path("exchange").asText(null));
      quote.setCurrency(json.path("currency").asText(null));
      quote.setDatetime(json.path("datetime").asText(null));
      quote.setOpen(json.path("open").asDouble(0.0));
      quote.setHigh(json.path("high").asDouble(0.0));
      quote.setLow(json.path("low").asDouble(0.0));
      quote.setClose(json.path("close").asDouble(0.0));
      quote.setVolume(json.path("volume").asLong(0L));
      quote.setPreviousClose(json.path("previous_close").asDouble(0.0));
      quote.setChange(json.path("change").asDouble(0.0));
      quote.setPercentChange(json.path("percent_change").asDouble(0.0));
      quote.setMarketOpen(json.path("is_market_open").asBoolean(false));
      if (!Double.isFinite(quote.getClose()) || quote.getClose() <= 0.0) {
        log.warn("Skipping TwelveData quote for {}: no positive close price", quote.getSymbol());
        return null;
      }
      return quote;
    } catch (Exception e) {
      log.error("Failed to parse MarketQuote: {}", e.getMessage(), e);
      return null;
    }
  }

  /** Single GET path; centralises timeout, URL building, and error mapping. */
  JsonNode get(String path, Map<String, String> params) throws TwelveDataException {
    return get(path, params, apiKey);
  }

  private JsonNode get(String path, Map<String, String> params, String requestApiKey)
      throws TwelveDataException {
    StringBuilder sb = new StringBuilder(baseUrl).append(path).append('?');
    Map<String, String> all = new HashMap<>(params);
    all.put("apikey", requestApiKey);
    boolean first = true;
    for (Map.Entry<String, String> e : all.entrySet()) {
      if (!first) {
        sb.append('&');
      }
      sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
      first = false;
    }

    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(sb.toString())).timeout(TIMEOUT).GET().build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new TwelveDataException(
            "TwelveData HTTP " + response.statusCode() + " for " + path, null);
      }
      return objectMapper.readTree(response.body());
    } catch (IOException e) {
      throw new TwelveDataException("TwelveData IO error for " + path + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TwelveDataException("TwelveData call interrupted for " + path, e);
    }
  }

  private static String encode(String value) {
    return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
