package com.smartbox.investory.integrations.fx.exchangeratehost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * Native HTTP client for the exchangerate.host API.
 *
 * <p>Originally a Spring Cloud OpenFeign {@code @FeignClient}; replaced with a small {@link
 * java.net.http.HttpClient} wrapper to drop the Spring Cloud dependency tree. The public surface
 * (method signature + {@link ExchangeRateResponse} DTO) is kept unchanged so existing callers and
 * tests keep compiling without changes.
 *
 * <p>The internal {@link #get(String, Map)} helper mirrors {@code TwelveDataService}: the parameter
 * map is URL-encoded, a single shared {@link HttpClient} drives every request with a 5 s timeout,
 * and IO/HTTP/interruption failures are wrapped in {@link ExchangeRateException}.
 */
@Slf4j
@Component
public class ExchangeRateClient {

  private static final String BASE_URL = "https://api.exchangerate.host";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public ExchangeRateClient(ObjectMapper objectMapper) {
    this(
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        objectMapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
  }

  // Visible for tests that want to inject a stub HttpClient / ObjectMapper.
  public ExchangeRateClient(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public ExchangeRateResponse getLatestRates(String source, String currencies, String apiKey) {
    return getLatestRates(source, currencies, apiKey, BASE_URL);
  }

  public ExchangeRateResponse getLatestRates(
      String source, String currencies, String apiKey, String baseUrl) {
    // LinkedHashMap keeps the query string deterministic (helps test assertions and log lines).
    Map<String, String> params = new LinkedHashMap<>();
    params.put("source", source);
    params.put("currencies", currencies);
    params.put("access_key", apiKey);
    String body = get(baseUrl, "/live", params);
    try {
      return objectMapper.readValue(body, ExchangeRateResponse.class);
    } catch (JacksonException e) {
      throw new ExchangeRateException("Failed to parse exchangerate.host response", e);
    }
  }

  /** Single GET path; centralises timeout, URL building, and error mapping. */
  public String get(String path, Map<String, String> params) {
    return get(BASE_URL, path, params);
  }

  public String get(String baseUrl, String path, Map<String, String> params) {
    StringBuilder sb =
        new StringBuilder(
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
            .append(path);
    if (!CollectionUtils.isEmpty(params)) {
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

    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(sb.toString())).timeout(TIMEOUT).GET().build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
    private LocalDate date;

    public Map<String, Double> getQuotes() {
      return quotes;
    }

    public void setQuotes(Map<String, Double> quotes) {
      this.quotes = quotes;
    }

    public LocalDate getDate() {
      return date;
    }

    public void setDate(LocalDate date) {
      this.date = date;
    }
  }
}
