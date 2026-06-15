package com.example.demo.services;


import com.example.demo.data.repository.FundamentalIndicator;
import com.example.demo.services.models.StockQuote;
import com.example.demo.services.models.TechnicalIndicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class TwelveDataService {
    @Value("${app.api.twelve-data-key}")
    private String apiKey;

    public TechnicalIndicator fetchTechnicalIndicatorsFromTwelveData(String symbol) {
        TechnicalIndicator result = new TechnicalIndicator();
        result.setSymbol(symbol);
        result.setTimestamp(ZonedDateTime.now().now());
//        result.sesetSyncDate(LocalDateTime.now());

        try {
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();

            // Fetch MACD
            String macdUrl = "https://api.twelvedata.com/macd?symbol=" + symbol + "&interval=1day&apikey=" + apiKey;
            HttpRequest macdRequest = HttpRequest.newBuilder().uri(URI.create(macdUrl)).build();
            String macdBody = client.send(macdRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode macdJson = mapper.readTree(macdBody);
            result.setMacd(macdJson.path("values").get(0).path("macd").asDouble());

            // Fetch RSI
            String rsiUrl = "https://api.twelvedata.com/rsi?symbol=" + symbol + "&interval=1day&time_period=14&apikey=" + apiKey;
            HttpRequest rsiRequest = HttpRequest.newBuilder().uri(URI.create(rsiUrl)).build();
            String rsiBody = client.send(rsiRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode rsiJson = mapper.readTree(rsiBody);
            result.setRsi(rsiJson.path("values").get(0).path("rsi").asDouble());

            // Fetch Volume from time_series
            String priceUrl = "https://api.twelvedata.com/time_series?symbol=" + symbol + "&interval=1day&outputsize=1&apikey=" + apiKey;
            HttpRequest priceRequest = HttpRequest.newBuilder().uri(URI.create(priceUrl)).build();
            String priceBody = client.send(priceRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode priceJson = mapper.readTree(priceBody);
            result.setVolume(priceJson.path("values").get(0).path("volume").asLong());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch technical indicators for " + symbol, e);
        }
    }

    public Map<String, TechnicalIndicator> fetchFundamentalIndicator(Set<String> symbols) throws Exception {
        Map<String, TechnicalIndicator> indicators = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        for (String symbol : symbols) {
            try {
                TechnicalIndicator indicator = new TechnicalIndicator();
                indicator.setSymbol(symbol);

                // Fetch MACD
                String macdUrl = "https://api.twelvedata.com/macd?symbol=" + symbol + "&interval=1day&apikey=" + apiKey;
                HttpRequest macdRequest = HttpRequest.newBuilder().uri(URI.create(macdUrl)).build();
                String macdBody = client.send(macdRequest, HttpResponse.BodyHandlers.ofString()).body();
                JsonNode macdJson = mapper.readTree(macdBody);
                indicator.setMacd(macdJson.path("values").get(0).path("macd").asDouble());

                // Fetch RSI
                String rsiUrl = "https://api.twelvedata.com/rsi?symbol=" + symbol + "&interval=1day&time_period=14&apikey=" + apiKey;
                HttpRequest rsiRequest = HttpRequest.newBuilder().uri(URI.create(rsiUrl)).build();
                String rsiBody = client.send(rsiRequest, HttpResponse.BodyHandlers.ofString()).body();
                JsonNode rsiJson = mapper.readTree(rsiBody);
                indicator.setRsi(rsiJson.path("values").get(0).path("rsi").asDouble());

                indicators.put(symbol, indicator);

            } catch (Exception e) {
                System.err.println("Failed to fetch MACD/RSI for " + symbol + ": " + e.getMessage());
            }
        }

        // Fetch volume in batch
        try {
            String joinedSymbols = String.join(",", symbols);
            String volumeUrl = "https://api.twelvedata.com/time_series?symbol=" + joinedSymbols + "&interval=1day&outputsize=1&apikey=" + apiKey;
            HttpRequest volumeRequest = HttpRequest.newBuilder().uri(URI.create(volumeUrl)).build();
            String volumeBody = client.send(volumeRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode volumeJson = mapper.readTree(volumeBody);

            for (String symbol : symbols) {
                JsonNode values = volumeJson.path(symbol).path("values");
                if (values != null && values.isArray() && values.size() > 0) {
                    long volume = values.get(0).path("volume").asLong();
                    TechnicalIndicator ind = indicators.get(symbol);
                    if (ind != null) ind.setVolume(volume);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch volume batch: " + e.getMessage());
        }

        return indicators;
    }

    public Map<String, TechnicalIndicator> fetchTechnicalIndicator(Set<String> symbols) throws Exception {
        Map<String, TechnicalIndicator> indicators = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        for (String symbol : symbols) {
            try {
                TechnicalIndicator indicator = new TechnicalIndicator();
                indicator.setSymbol(symbol);

                // Fetch MACD
                String macdUrl = "https://api.twelvedata.com/macd?symbol=" + symbol + "&interval=1day&apikey=" + apiKey;
                HttpRequest macdRequest = HttpRequest.newBuilder().uri(URI.create(macdUrl)).build();
                String macdBody = client.send(macdRequest, HttpResponse.BodyHandlers.ofString()).body();
                JsonNode macdJson = mapper.readTree(macdBody);
                indicator.setMacd(macdJson.path("values").get(0).path("macd").asDouble());

                // Fetch RSI
                String rsiUrl = "https://api.twelvedata.com/rsi?symbol=" + symbol + "&interval=1day&time_period=14&apikey=" + apiKey;
                HttpRequest rsiRequest = HttpRequest.newBuilder().uri(URI.create(rsiUrl)).build();
                String rsiBody = client.send(rsiRequest, HttpResponse.BodyHandlers.ofString()).body();
                JsonNode rsiJson = mapper.readTree(rsiBody);
                indicator.setRsi(rsiJson.path("values").get(0).path("rsi").asDouble());

                indicators.put(symbol, indicator);

            } catch (Exception e) {
                System.err.println("Failed to fetch MACD/RSI for " + symbol + ": " + e.getMessage());
            }
        }

        // Fetch volume in batch
        try {
            String joinedSymbols = String.join(",", symbols);
            String volumeUrl = "https://api.twelvedata.com/time_series?symbol=" + joinedSymbols + "&interval=1day&outputsize=1&apikey=" + apiKey;
            HttpRequest volumeRequest = HttpRequest.newBuilder().uri(URI.create(volumeUrl)).build();
            String volumeBody = client.send(volumeRequest, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode volumeJson = mapper.readTree(volumeBody);

            for (String symbol : symbols) {
                JsonNode values = volumeJson.path(symbol).path("values");
                if (values != null && values.isArray() && values.size() > 0) {
                    long volume = values.get(0).path("volume").asLong();
                    TechnicalIndicator ind = indicators.get(symbol);
                    if (ind != null) ind.setVolume(volume);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch volume batch: " + e.getMessage());
        }

        return indicators;
    }

    /**
     * Fetches monthly closing prices for a symbol, keyed by "yyyy-MM" (chronological).
     * Used for benchmark comparison (e.g. SPY).
     */
    public java.util.NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
        java.util.TreeMap<String, Double> closes = new java.util.TreeMap<>();
        try {
            String url = "https://api.twelvedata.com/time_series?symbol=" + symbol
                    + "&interval=1month&outputsize=" + months + "&apikey=" + apiKey;
            ObjectMapper mapper = new ObjectMapper();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode json = mapper.readTree(body);
            JsonNode values = json.path("values");
            if (values.isArray()) {
                for (JsonNode value : values) {
                    String datetime = value.path("datetime").asText();
                    if (datetime.length() >= 7) {
                        closes.put(datetime.substring(0, 7), value.path("close").asDouble());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch monthly closes for {}: {}", symbol, e.getMessage());
        }
        return closes;
    }

    public Map<String, StockQuote> fetchStockQuotes(String joinedSymbols) throws Exception {
        String urlString = "https://api.twelvedata.com/quote?symbol=" + joinedSymbols + "&apikey=" + apiKey;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("HTTP error code: " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            output.append(line);
        }
        conn.disconnect();

        return parseQuotes(output.toString());
    }

    private Map<String, StockQuote> parseQuotes(String jsonResponse) {
        JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
        Map<String, StockQuote> quotes = new HashMap<>();

        if (root.get("code") != null && !"200".equalsIgnoreCase(root.get("code").toString())) {
            throw new IllegalArgumentException(root.get("message").toString());
        }

        Set<String> symbols = root.keySet();
        if (symbols.contains("symbol")) {
            symbols = Set.of(root.get("symbol").getAsString());
        }
        symbols.forEach(symbol -> {
            StockQuote quote = parseQuote(symbol, root);
            if (quote != null) {
                quotes.put(symbol, quote);
            }
        });
        return quotes;
    }

    private StockQuote parseQuote(String symbol, JsonObject root) {
        try {
            JsonObject json = !root.has(symbol) ? root : root.getAsJsonObject(symbol);
            if (json.has("code")) {
                return null;
            }

            StockQuote quote = new StockQuote();
            quote.setSymbol(json.get("symbol").getAsString());
            quote.setName(json.get("name").getAsString());
            quote.setExchange(json.get("exchange").getAsString());
            quote.setCurrency(json.get("currency").getAsString());
            quote.setDatetime(json.get("datetime").getAsString());
            quote.setOpen(json.get("open").getAsDouble());
            quote.setHigh(json.get("high").getAsDouble());
            quote.setLow(json.get("low").getAsDouble());
            quote.setClose(json.get("close").getAsDouble());
            quote.setVolume(json.get("volume").getAsLong());
            quote.setPreviousClose(json.get("previous_close").getAsDouble());
            quote.setChange(json.get("change").getAsDouble());
            quote.setPercentChange(json.get("percent_change").getAsDouble());
            quote.setMarketOpen(json.get("is_market_open").getAsBoolean());
//            quote.setDividendYield(json.get("dividend_yield").getAsDouble());
//            quote.setEps(json.get("eps").getAsDouble());
//            quote.setPeRatio(json.get("pe_ratio").getAsDouble());
            return quote;
        } catch (Exception e) {
            log.error("Failed to parse StockQuote: {} - {}", symbol, e.getMessage(), e);
            return null;
        }
    }
}

