package com.smartbox.investory.integrations.fx.nbp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** Small HTTP client for the public NBP Table A API. */
@Component
public class NbpClient {
  static final String DEFAULT_BASE_URL = "https://api.nbp.pl/api";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public NbpClient(ObjectMapper objectMapper) {
    this(
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        objectMapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
  }

  NbpClient(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public List<NbpTable> findTables(LocalDate from, LocalDate to, String baseUrl) {
    String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    URI uri = URI.create(root + "/exchangerates/tables/a/" + from + "/" + to + "/?format=json");
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404) return List.of();
      if (response.statusCode() / 100 != 2) {
        throw new NbpException("NBP returned HTTP " + response.statusCode());
      }
      return Arrays.asList(objectMapper.readValue(response.body(), NbpTable[].class));
    } catch (NbpException e) {
      throw e;
    } catch (IOException e) {
      throw new NbpException("Failed to call NBP", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NbpException("Interrupted while calling NBP", e);
    } catch (RuntimeException e) {
      throw new NbpException("Failed to parse NBP response", e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NbpTable {
    private LocalDate effectiveDate;
    private List<NbpRate> rates;

    public LocalDate getEffectiveDate() {
      return effectiveDate;
    }

    public void setEffectiveDate(LocalDate value) {
      effectiveDate = value;
    }

    public List<NbpRate> getRates() {
      return rates;
    }

    public void setRates(List<NbpRate> value) {
      rates = value;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NbpRate {
    private String code;
    private java.math.BigDecimal mid;

    public String getCode() {
      return code;
    }

    public void setCode(String value) {
      code = value;
    }

    public java.math.BigDecimal getMid() {
      return mid;
    }

    public void setMid(java.math.BigDecimal value) {
      mid = value;
    }
  }

  public static class NbpException extends RuntimeException {
    public NbpException(String message) {
      super(message);
    }

    public NbpException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
