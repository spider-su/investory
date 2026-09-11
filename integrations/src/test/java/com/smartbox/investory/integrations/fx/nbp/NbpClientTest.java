package com.smartbox.investory.integrations.fx.nbp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NbpClientTest {
  private final HttpClient httpClient = mock();
  private final HttpResponse<String> response = mock();
  private final NbpClient client = new NbpClient(httpClient, new ObjectMapper());

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void missingNbpRangeIsAnEmptyResult() throws Exception {
    when(response.statusCode()).thenReturn(404);
    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    assertThat(client.findTables(date(1), date(2), "https://nbp.example/api/")).isEmpty();
  }

  @Test
  void successfulResponseIsDeserialized() throws Exception {
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "[{\"effectiveDate\":\"2026-08-01\",\"rates\":[{\"code\":\"USD\",\"mid\":4.02}]}]");
    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    List<NbpClient.NbpTable> tables =
        client.findTables(date(1), date(2), "https://nbp.example/api");

    assertThat(tables).hasSize(1);
    assertThat(tables.getFirst().getEffectiveDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(tables.getFirst().getRates().getFirst().getCode()).isEqualTo("USD");
  }

  @Test
  void nonSuccessfulResponseIsReportedAsNbpFailure() throws Exception {
    when(response.statusCode()).thenReturn(503);
    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    assertThatThrownBy(() -> client.findTables(date(1), date(2), "https://nbp.example/api"))
        .isInstanceOf(NbpClient.NbpException.class)
        .hasMessage("NBP returned HTTP 503");
  }

  @Test
  void malformedOrInterruptedResponsesFailClosed() throws Exception {
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("not-json");
    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);

    assertThatThrownBy(() -> client.findTables(date(1), date(2), "https://nbp.example/api"))
        .isInstanceOf(NbpClient.NbpException.class)
        .hasMessage("Failed to parse NBP response");

    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new InterruptedException("cancelled"));

    assertThatThrownBy(() -> client.findTables(date(1), date(2), "https://nbp.example/api"))
        .isInstanceOf(NbpClient.NbpException.class)
        .hasMessage("Interrupted while calling NBP");
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void ioFailureIsWrapped() throws Exception {
    when(httpClient.send(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("offline"));

    assertThatThrownBy(() -> client.findTables(date(1), date(2), "https://nbp.example/api"))
        .isInstanceOf(NbpClient.NbpException.class)
        .hasMessage("Failed to call NBP");
  }

  private static LocalDate date(int day) {
    return LocalDate.of(2026, 8, day);
  }
}
