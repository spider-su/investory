package com.example.demo.services.notifications;

import com.example.demo.data.repository.Stock;
import com.example.demo.data.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BigMoveAlertRuleTest {

    @Mock
    private StockRepository stockRepository;

    private NotificationProperties properties;
    private BigMoveAlertRule rule;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setBigMoveThresholdPct(5.0);
        rule = new BigMoveAlertRule(stockRepository, properties);
    }

    @Test
    void evaluate_firesOnLargeUpAndDownMoves() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("AAPL.US", 100.0, 107.0),   // +7%
                stock("MSFT.US", 100.0, 92.0),    // -8%
                stock("PG.US", 50.0, 51.0)        // +2%, ignored
        ));

        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("AAPL.US"));
        assertTrue(result.get().contains("MSFT.US"));
        assertFalse(result.get().contains("PG.US"));
    }

    @Test
    void evaluate_isQuietWhenNoStocksMoveEnough() {
        when(stockRepository.findAll()).thenReturn(List.of(stock("AAPL.US", 100.0, 102.0)));

        assertFalse(rule.evaluate().isPresent());
    }

    @Test
    void evaluate_isSafeWithMissingPrices() {
        when(stockRepository.findAll()).thenReturn(List.of(
                stock("AAPL.US", null, 100.0),
                stock("MSFT.US", 0.0, 50.0),
                stock("PG.US", 50.0, null)
        ));

        assertFalse(rule.evaluate().isPresent());
    }

    private static Stock stock(String symbol, Double open, Double price) {
        Stock s = new Stock();
        s.setSymbol(symbol);
        s.setDayOpenPrice(open);
        s.setMarketPrice(price);
        return s;
    }
}

