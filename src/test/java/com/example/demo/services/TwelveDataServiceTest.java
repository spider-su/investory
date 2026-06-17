package com.example.demo.services;

import com.example.demo.clients.TwelveDataService;
import com.example.demo.services.models.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwelveDataServiceTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> response;

    private TwelveDataService service;

    @BeforeEach
    void setUp() {
        service = new TwelveDataService();
        service.setHttpClient(httpClient);
        service.setApiKey("test-key");
    }

    @Test
    void fetchMonthlyCloses_parsesYyyyMmKeyedCloses() throws Exception {
        stubResponse(200, """
                {
                  "meta": {"symbol": "SPY"},
                  "values": [
                    {"datetime": "2026-05-31", "close": "535.20"},
                    {"datetime": "2026-04-30", "close": "510.10"},
                    {"datetime": "2026-03-31", "close": "490.00"}
                  ]
                }
                """);

        NavigableMap<String, Double> closes = service.fetchMonthlyCloses("SPY", 3);

        assertEquals(3, closes.size());
        assertEquals(535.20, closes.get("2026-05"), 0.0001);
        assertEquals(510.10, closes.get("2026-04"), 0.0001);
        assertEquals("2026-03", closes.firstKey());
        assertEquals("2026-05", closes.lastKey());
    }

    @Test
    void fetchMonthlyCloses_returnsEmptyMapOnHttpError() throws Exception {
        stubResponse(429, "rate limited");

        NavigableMap<String, Double> closes = service.fetchMonthlyCloses("SPY", 12);

        assertTrue(closes.isEmpty());
    }

    @Test
    void fetchStockQuotes_parsesMultiSymbolResponse() throws Exception {
        stubResponse(200, """
                {
                  "AAPL": {"symbol":"AAPL","name":"Apple","exchange":"NASDAQ","currency":"USD",
                            "datetime":"2026-06-12","open":"180","high":"182","low":"179",
                            "close":"181","volume":"123","previous_close":"180",
                            "change":"1","percent_change":"0.5","is_market_open":true},
                  "MSFT": {"symbol":"MSFT","name":"Microsoft","exchange":"NASDAQ","currency":"USD",
                            "datetime":"2026-06-12","open":"420","high":"422","low":"419",
                            "close":"421","volume":"456","previous_close":"420",
                            "change":"1","percent_change":"0.2","is_market_open":true}
                }
                """);

        Map<String, StockQuote> quotes = service.fetchStockQuotes("AAPL,MSFT");

        assertEquals(2, quotes.size());
        assertEquals(181.0, quotes.get("AAPL").getClose(), 0.0001);
        assertEquals(421.0, quotes.get("MSFT").getClose(), 0.0001);
        assertEquals("USD", quotes.get("AAPL").getCurrency());
    }

    @Test
    void fetchStockQuotes_parsesSingleSymbolResponse() throws Exception {
        stubResponse(200, """
                {"symbol":"AAPL","name":"Apple","exchange":"NASDAQ","currency":"USD",
                 "datetime":"2026-06-12","open":"180","high":"182","low":"179","close":"181",
                 "volume":"123","previous_close":"180","change":"1","percent_change":"0.5",
                 "is_market_open":true}
                """);

        Map<String, StockQuote> quotes = service.fetchStockQuotes("AAPL");

        assertEquals(1, quotes.size());
        StockQuote q = quotes.get("AAPL");
        assertNotNull(q);
        assertEquals(181.0, q.getClose(), 0.0001);
        assertEquals("AAPL", q.getSymbol());
    }

    @Test
    void fetchStockQuotes_throwsIllegalArgumentOnApiErrorPayload() throws Exception {
        stubResponse(200, """
                {"code": 401, "message": "Invalid API key"}
                """);

        assertThrows(IllegalArgumentException.class, () -> service.fetchStockQuotes("AAPL,MSFT"));
    }

    @Test
    void fetchStockQuotes_wrapsHttpFailureInRuntimeException() throws Exception {
        stubResponse(500, "boom");

        assertThrows(RuntimeException.class, () -> service.fetchStockQuotes("AAPL"));
    }

    @Test
    void getEncodesApiKeyAndParameters() throws Exception {
        stubResponse(200, "{}");

        service.fetchMonthlyCloses("SP Y", 1);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(requestCaptor.capture(), any());
        String url = requestCaptor.getValue().uri().toString();
        // Symbol with space must be URL-encoded; api key included.
        assertTrue(url.contains("symbol=SP+Y") || url.contains("symbol=SP%20Y"),
                "expected encoded symbol in: " + url);
        assertTrue(url.contains("apikey=test-key"), "expected api key in: " + url);
    }

    private void stubResponse(int status, String body) throws Exception {
        // The generic <String> binding is needed so Mockito can match
        // the parameterised HttpClient.send(HttpRequest, BodyHandler<T>) overload.
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(status);
        if (status / 100 == 2) {
            when(response.body()).thenReturn(body);
        }
    }
}





