package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import com.example.demo.services.models.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock private TwelveDataService twelveDataService;
    @Mock private OpenedPositionRepository openedPositionRepository;
    @Mock private StockRepository stockRepository;
    @Mock private HistoryService historyService;
    @Mock private AccountSummaryRepository accountSummaryRepository;

    private MarketService marketService;

    @BeforeEach
    void setUp() {
        marketService = new MarketService(twelveDataService, openedPositionRepository,
                stockRepository, historyService, accountSummaryRepository);
    }

    @Test
    void splitIntoChunks_dividesMapEvenly() {
        LinkedHashMap<Integer, Integer> input = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            input.put(i, i);
        }

        List<Map<Integer, Integer>> chunks = MarketService.splitIntoChunks(input, 3);

        assertEquals(4, chunks.size());
        assertEquals(3, chunks.get(0).size());
        assertEquals(3, chunks.get(1).size());
        assertEquals(3, chunks.get(2).size());
        assertEquals(1, chunks.get(3).size());
    }

    @Test
    void splitIntoChunks_returnsEmptyListForEmptyMap() {
        assertEquals(0, MarketService.splitIntoChunks(new LinkedHashMap<>(), 5).size());
    }

    @Test
    void createStocks_addsRowForUsXtbSymbol() {
        OpenedPosition position = new OpenedPosition();
        position.setSymbol("AAPL.US");
        position.setAccount("12345");
        position.setCurrency(CurrencyType.USD);
        position.setVolume(10.0);
        position.setOpenPrice(150.0);
        position.setMarketPrice(155.0);
        position.setProfit(50.0);

        when(openedPositionRepository.findAll()).thenReturn(List.of(position));
        when(stockRepository.findAll()).thenReturn(List.of());

        marketService.createStocks();

        org.mockito.ArgumentCaptor<Iterable<Stock>> captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(stockRepository).saveAll(captor.capture());
        List<Stock> saved = toList(captor.getValue());
        assertEquals(1, saved.size());
        Stock created = saved.get(0);
        assertEquals("AAPL.US", created.getSymbol());
        assertEquals("AAPL", created.getTicker());
        assertEquals(10.0, created.getAmount());
        assertEquals(150.0, created.getOpenPrice());
        assertEquals(155.0, created.getMarketPrice());
    }

    @Test
    void createStocks_skipsNonUsXtbOnlyPositions() {
        OpenedPosition position = new OpenedPosition();
        position.setSymbol("BMW.DE"); // non-US, non-IBKR
        position.setAccount("XTB-DE");
        position.setCurrency(CurrencyType.EUR);
        position.setVolume(1.0);
        position.setOpenPrice(80.0);

        when(openedPositionRepository.findAll()).thenReturn(List.of(position));
        when(stockRepository.findAll()).thenReturn(List.of());

        marketService.createStocks();

        org.mockito.ArgumentCaptor<Iterable<Stock>> captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(stockRepository).saveAll(captor.capture());
        assertEquals(0, toList(captor.getValue()).size());
    }

    @Test
    void syncIbkrPositions_appliesMarketPriceAndUpdatesEquity() {
        OpenedPosition ibkr = new OpenedPosition();
        ibkr.setSymbol("AAPL");
        ibkr.setAccount("IBKR");
        ibkr.setCurrency(CurrencyType.USD);
        ibkr.setVolume(10.0);
        ibkr.setOpenPrice(150.0);

        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        stock.setMarketPrice(180.0);

        AccountSummary summary = new AccountSummary();
        summary.setAccount("IBKR");
        summary.setCurrency(CurrencyType.USD);
        summary.setBalance(500.0);
        summary.setEquity(0.0);

        when(openedPositionRepository.findAllByAccount("IBKR")).thenReturn(List.of(ibkr));
        when(stockRepository.findAll()).thenReturn(List.of(stock));
        when(accountSummaryRepository.findById("IBKR")).thenReturn(Optional.of(summary));

        marketService.syncIbkrPositions();

        assertEquals(180.0, ibkr.getMarketPrice());
        assertEquals(300.0, ibkr.getProfit(), 0.01); // 10 * (180 - 150)
        assertEquals(500.0 + (10 * 180.0), summary.getEquity(), 0.01);
        verify(openedPositionRepository).saveAll(List.of(ibkr));
        verify(accountSummaryRepository).save(summary);
    }

    @Test
    void syncIbkrPositions_isNoopWhenNoPositions() {
        when(openedPositionRepository.findAllByAccount("IBKR")).thenReturn(List.of());

        marketService.syncIbkrPositions();

        verify(accountSummaryRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateStocks_skipsUnsupportedSymbolsAndPersistsQuoteData() throws Exception {
        Stock supported = new Stock();
        supported.setSymbol("AAPL.US");
        supported.setTicker("AAPL");
        supported.setAmount(5.0);
        supported.setOpenPrice(100.0);
        supported.setUpdatedDate(java.time.ZonedDateTime.now().minusHours(1));

        Stock unsupported = new Stock();
        unsupported.setSymbol("CSPX.UK");
        unsupported.setTicker("CSPX");
        unsupported.setAmount(2.0);
        unsupported.setOpenPrice(50.0);
        unsupported.setUpdatedDate(java.time.ZonedDateTime.now().minusHours(2));

        when(stockRepository.findAll()).thenReturn(List.of(unsupported, supported));

        StockQuote quote = new StockQuote();
        quote.setSymbol("AAPL");
        quote.setClose(110.0);
        quote.setOpen(108.0);
        quote.setCurrency("USD");
        when(twelveDataService.fetchStockQuotes(anyString()))
                .thenReturn(Map.of("AAPL", quote));

        // Production code sleeps 2 minutes between chunks; run on a daemon thread and bail out as
        // soon as the single chunk has been applied. The interrupted-sleep stack trace is logged
        // to stderr (cosmetic only) but does not fail the test.
        Thread runner = new Thread(() -> {
            try {
                marketService.updateStocks();
            } catch (RuntimeException expected) {
                // Caused by the deliberate interrupt below.
            }
        }, "market-test-runner");
        runner.setDaemon(true);
        runner.start();
        for (int i = 0; i < 50 && supported.getMarketPrice() == null; i++) {
            Thread.sleep(50);
        }
        runner.interrupt();
        runner.join(1000);

        assertEquals(110.0, supported.getMarketPrice());
        assertEquals(108.0, supported.getDayOpenPrice());
        assertEquals(5.0 * (110.0 - 100.0), supported.getProfit(), 0.01);
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        java.util.ArrayList<T> list = new java.util.ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }
}

