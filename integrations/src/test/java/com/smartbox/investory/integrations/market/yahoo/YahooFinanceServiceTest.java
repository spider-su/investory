package com.smartbox.investory.integrations.market.yahoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Yahoo Finance Service")
class YahooFinanceServiceTest {

  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> response;

  private YahooFinanceService service;

  @BeforeEach
  void setUp() {
    service = new YahooFinanceService();
    service.setHttpClient(httpClient);
  }

  @DisplayName("fetch Latest Quote Parses Public Chart Response")
  @Test
  void fetchLatestQuoteParsesPublicChartResponse() throws Exception {
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {"chart":{"result":[{"meta":{"currency":"USD","regularMarketPrice":194.80,"regularMarketTime":1786548358}}]}}
            """);
    when(httpClient.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    YahooFinanceService.YahooQuote quote = service.fetchLatestQuote("VWRA.L").orElseThrow();

    assertEquals("VWRA.L", quote.symbol());
    assertEquals("USD", quote.currency());
    assertEquals(194.80, quote.price(), 0.000001);
    assertEquals("2026-08-12", quote.date().toString());
  }

  @DisplayName("fetch Latest Quote Returns Empty For Unavailable Symbol")
  @Test
  void fetchLatestQuoteReturnsEmptyForUnavailableSymbol() throws Exception {
    when(response.statusCode()).thenReturn(404);
    when(httpClient.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    assertTrue(service.fetchLatestQuote("UNKNOWN.L").isEmpty());
  }

  @DisplayName("fetch Latest Quote Propagates Transport Failure")
  @Test
  void fetchLatestQuotePropagatesTransportFailure() throws Exception {
    when(httpClient.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("network unavailable"));

    assertThrows(IllegalStateException.class, () -> service.fetchLatestQuote("VWRA.L"));
  }
}
