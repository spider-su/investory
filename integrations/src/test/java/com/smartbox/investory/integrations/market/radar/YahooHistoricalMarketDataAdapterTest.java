package com.smartbox.investory.integrations.market.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class YahooHistoricalMarketDataAdapterTest {
  @ParameterizedTest
  @ValueSource(strings = {"^GSPC", "^VIX"})
  void preservesIndexSymbolAndSourceDatesWithoutInventingMissingCloses(String symbol)
      throws Exception {
    HttpClient client = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    LocalDate from = LocalDate.of(2026, 7, 1), to = LocalDate.of(2026, 7, 8);
    long start = from.atTime(20, 0).toEpochSecond(ZoneOffset.UTC);
    double close = symbol.equals("^GSPC") ? 6100 : 20;
    // Valid first/last closes, null/zero/negative/text closes, and a missing final close.
    String body =
        """
        {"chart":{"result":[{"timestamp":[%d,%d,%d,%d,%d,%d,%d],
        "indicators":{"quote":[{"close":[%s,null,0,-2,"invalid",%s]}]}}],"error":null}}
        """
            .formatted(
                start,
                start + 86400,
                start + 172800,
                start + 259200,
                start + 345600,
                start + 432000,
                start + 518400,
                close,
                close + 1);
    when(response.body()).thenReturn(body);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    var service = new YahooFinanceService(new ObjectMapper());
    service.setHttpClient(client);
    var result = new YahooHistoricalMarketDataAdapter(service).dailyBars(symbol, from, to);
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
    assertThat(request.getValue().uri().getPath()).endsWith("/" + symbol);
    assertThat(request.getValue().uri().getRawPath()).endsWith("/%5E" + symbol.substring(1));
    assertThat(request.getValue().uri().getQuery()).contains("interval=1d");
    assertThat(result).hasSize(2);
    assertThat(result.getFirst().date()).isEqualTo(from);
    assertThat(result.getLast().date()).isEqualTo(from.plusDays(5));
    assertThat(result.getFirst().close()).isEqualTo(close);
    assertThat(result.getLast().close()).isEqualTo(close + 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"^GSPC", "^VIX"})
  void missingChartProducesNoIndexObservations(String symbol) throws Exception {
    HttpClient client = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"chart\":{\"result\":[],\"error\":null}}");
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    var service = new YahooFinanceService(new ObjectMapper());
    service.setHttpClient(client);
    assertThat(
            new YahooHistoricalMarketDataAdapter(service)
                .dailyBars(symbol, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8)))
        .isEmpty();
  }
}
