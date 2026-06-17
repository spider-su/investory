package com.example.demo.clients;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for the native HTTP layer in {@link ExchangeRateClient}.
 *
 * <p>{@code CurrencyRateUpdaterServiceTest} mocks the client itself; this class
 * pins the HTTP plumbing (URL building, status-code handling, error mapping) so
 * regressions in the native {@code java.net.http} wiring are caught locally.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateClientTest {

    @Mock
    private HttpClient httpClient;

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void getLatestRates_buildsLiveUrlAndParsesQuotes() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"quotes\":{\"USDEUR\":0.9,\"USDPLN\":4.0}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        ExchangeRateClient client = new ExchangeRateClient(httpClient, objectMapper);

        ExchangeRateClient.ExchangeRateResponse parsed =
                client.getLatestRates("USD", "USD,EUR,PLN", "test-key");

        assertEquals(0.9, parsed.getQuotes().get("USDEUR"));
        assertEquals(4.0, parsed.getQuotes().get("USDPLN"));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient)
                .send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        URI uri = requestCaptor.getValue().uri();
        assertEquals("api.exchangerate.host", uri.getHost());
        assertEquals("/live", uri.getPath());
        // Comma in `currencies` must be URL-encoded; access_key must be present.
        String query = uri.getRawQuery();
        assertTrue(query.contains("source=USD"), query);
        assertTrue(query.contains("currencies=USD%2CEUR%2CPLN"), query);
        assertTrue(query.contains("access_key=test-key"), query);
    }

    @Test
    void getLatestRates_throwsExchangeRateExceptionOnNon2xx() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        ExchangeRateClient client = new ExchangeRateClient(httpClient, objectMapper);

        ExchangeRateException ex = assertThrows(ExchangeRateException.class,
                () -> client.getLatestRates("USD", "USD,EUR,PLN", "test-key"));
        assertTrue(ex.getMessage().contains("503"), ex.getMessage());
    }

    @Test
    void getLatestRates_wrapsIoErrorInExchangeRateException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("boom"));

        ExchangeRateClient client = new ExchangeRateClient(httpClient, objectMapper);

        ExchangeRateException ex = assertThrows(ExchangeRateException.class,
                () -> client.getLatestRates("USD", "USD,EUR,PLN", "test-key"));
        assertTrue(ex.getCause() instanceof IOException);
    }

    @Test
    void getLatestRates_restoresInterruptFlag() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("cancelled"));

        ExchangeRateClient client = new ExchangeRateClient(httpClient, objectMapper);

        try {
            assertThrows(ExchangeRateException.class,
                    () -> client.getLatestRates("USD", "USD,EUR,PLN", "test-key"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear the interrupt flag so it doesn't leak into other tests on the same thread.
            assertTrue(Thread.interrupted());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void get_returnsBodyForArbitraryPathAndParams() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("ok");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        ExchangeRateClient client = new ExchangeRateClient(httpClient, objectMapper);

        String body = client.get("/list", Map.of("k", "v"));

        assertEquals("ok", body);
    }
}

