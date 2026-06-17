package com.example.demo.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native HTTP client for the exchangerate.host API.
 *
 * <p>Originally a Spring Cloud OpenFeign {@code @FeignClient}; replaced with a small
 * {@link java.net.http.HttpClient} wrapper to drop the Spring Cloud dependency tree.
 * The public surface (method signature + {@link ExchangeRateResponse} DTO) is kept
 * unchanged so existing callers and tests keep compiling without changes.
 *
 * <p>The internal {@link #get(String, Map)} helper mirrors {@code TwelveDataService}:
 * the parameter map is URL-encoded, a single shared {@link HttpClient} drives every
 * request with a 10 s timeout, and IO/HTTP/interruption failures are wrapped in
 * {@link ExchangeRateException}.
 */
@Slf4j
@Component
public class ExchangeRateClient {

    private static final String BASE_URL = "https://api.exchangerate.host";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExchangeRateClient() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    // Visible for tests that want to inject a stub HttpClient / ObjectMapper.
    ExchangeRateClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ExchangeRateResponse getLatestRates(String source, String currencies, String apiKey) {
        // LinkedHashMap keeps the query string deterministic (helps test assertions and log lines).
        Map<String, String> params = new LinkedHashMap<>();
        params.put("source", source);
        params.put("currencies", currencies);
        params.put("access_key", apiKey);
        String body = get("/live", params);
        try {
            return objectMapper.readValue(body, ExchangeRateResponse.class);
        } catch (IOException e) {
            throw new ExchangeRateException("Failed to parse exchangerate.host response", e);
        }
    }

    /** Single GET path; centralises timeout, URL building, and error mapping. */
    String get(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(BASE_URL).append(path);
        if (params != null && !params.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
                first = false;
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sb.toString()))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ExchangeRateException(
                        "exchangerate.host returned HTTP " + response.statusCode() + " for " + path);
            }
            return response.body();
        } catch (IOException e) {
            throw new ExchangeRateException("Failed to call exchangerate.host " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateException("Interrupted while calling exchangerate.host " + path, e);
        }
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExchangeRateResponse {
        private Map<String, Double> quotes;

        public Map<String, Double> getQuotes() {
            return quotes;
        }

        public void setQuotes(Map<String, Double> quotes) {
            this.quotes = quotes;
        }
    }
}
