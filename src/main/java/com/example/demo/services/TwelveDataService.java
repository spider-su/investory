package com.example.demo.services;

import com.example.demo.services.models.StockQuote;
import com.example.demo.services.models.TechnicalIndicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Native HTTP client for the TwelveData REST API.
 *
 * <p>Endpoints used: {@code /quote}, {@code /macd}, {@code /rsi}, {@code /time_series}.
 * The API key is sourced from {@code app.api.twelve-data-key}.
 *
 * <p>All requests go through the same {@link HttpClient} (reused) with a 10s timeout.
 * Tests can inject a stubbed {@code HttpClient} via the package-private constructor.
 */
@Slf4j
@Service
public class TwelveDataService {

    private static final String BASE_URL = "https://api.twelvedata.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Value("${app.api.twelve-data-key:}")
    private String apiKey;

    public TwelveDataService() {
        // Default constructor; HttpClient is initialised inline and apiKey is
        // injected via @Value. Field injection (rather than constructor injection)
        // is used so the IntelliJ Spring inspector is satisfied — a constructor
        // taking only @Value-bound String is flagged as non-autowireable.
    }

    /** Test seam: swap the underlying HTTP client (e.g. for a Mockito mock). */
    void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /** Test seam: set the API key without going through Spring property binding. */
    void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Fetches MACD + RSI + last-day volume for a single symbol. The result is partially
     * populated even if one of the calls misses (the missing fields stay at 0).
     */
    public TechnicalIndicator fetchTechnicalIndicatorsFromTwelveData(String symbol) {
        TechnicalIndicator result = new TechnicalIndicator();
        result.setSymbol(symbol);
        result.setTimestamp(ZonedDateTime.now());
        try {
            JsonNode macd = get("/macd", Map.of("symbol", symbol, "interval", "1day"));
            result.setMacd(macd.path("values").path(0).path("macd").asDouble(0.0));

            JsonNode rsi = get("/rsi", Map.of("symbol", symbol, "interval", "1day", "time_period", "14"));
            result.setRsi(rsi.path("values").path(0).path("rsi").asDouble(0.0));

            JsonNode series = get("/time_series",
                    Map.of("symbol", symbol, "interval", "1day", "outputsize", "1"));
            result.setVolume(series.path("values").path(0).path("volume").asLong(0L));
            return result;
        } catch (TwelveDataException e) {
            throw new RuntimeException("Failed to fetch technical indicators for " + symbol, e);
        }
    }

    /**
     * Fetches monthly closing prices for a symbol, keyed by "yyyy-MM" (chronological).
     * Used by {@code BenchmarkService} for the SPY comparison curve. Returns an empty map
     * on any error so the caller can fall back to its previous-day cached values.
     */
    public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
        NavigableMap<String, Double> closes = new TreeMap<>();
        try {
            JsonNode json = get("/time_series",
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
            log.error("Failed to fetch monthly closes for {}: {}", symbol, e.getMessage());
        }
        return closes;
    }

    /**
     * Fetches quote(s) by symbol. {@code joinedSymbols} is a comma-separated list of
     * tickers. Single-symbol responses come back as the quote at the JSON root;
     * multi-symbol responses come back as {@code {"AAPL": {...}, "MSFT": {...}}}.
     */
    public Map<String, StockQuote> fetchStockQuotes(String joinedSymbols) {
        Map<String, StockQuote> result = new LinkedHashMap<>();
        JsonNode root;
        try {
            root = get("/quote", Map.of("symbol", joinedSymbols));
        } catch (TwelveDataException e) {
            throw new RuntimeException("Failed to fetch quotes for " + joinedSymbols, e);
        }

        if (root.has("code") && root.path("code").asInt(200) != 200) {
            throw new IllegalArgumentException("TwelveData /quote error: " + root.path("message").asText());
        }
        if (root.has("symbol")) {
            // Single-symbol response: the quote is at the root.
            StockQuote single = parseQuote(root);
            if (single != null) {
                result.put(single.getSymbol(), single);
            }
            return result;
        }
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String symbol = fieldNames.next();
            StockQuote q = parseQuote(root.path(symbol));
            if (q != null) {
                result.put(symbol, q);
            }
        }
        return result;
    }

    private StockQuote parseQuote(JsonNode json) {
        if (json == null || json.isMissingNode() || json.has("code")) {
            return null;
        }
        try {
            StockQuote quote = new StockQuote();
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
            return quote;
        } catch (Exception e) {
            log.error("Failed to parse StockQuote: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Single GET path; centralises timeout, URL building, and error mapping. */
    JsonNode get(String path, Map<String, String> params) throws TwelveDataException {
        StringBuilder sb = new StringBuilder(BASE_URL).append(path).append('?');
        Map<String, String> all = new HashMap<>(params);
        all.put("apikey", apiKey);
        boolean first = true;
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
            first = false;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sb.toString()))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new TwelveDataException(
                        "TwelveData HTTP " + response.statusCode() + " for " + path, null);
            }
            return MAPPER.readTree(response.body());
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
