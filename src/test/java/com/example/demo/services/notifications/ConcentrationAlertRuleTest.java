package com.example.demo.services.notifications;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.OpenedPosition;
import com.example.demo.data.repository.OpenedPositionRepository;
import com.example.demo.services.CurrencyRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcentrationAlertRuleTest {

    @Mock
    private OpenedPositionRepository openedPositionRepository;
    @Mock
    private CurrencyRateService currencyRateService;

    private NotificationProperties properties;
    private ConcentrationAlertRule rule;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setConcentrationThresholdPct(25.0);
        rule = new ConcentrationAlertRule(openedPositionRepository, currencyRateService, properties);
        // Identity FX conversion for simplicity. lenient() because the empty-portfolio test skips conversion.
        org.mockito.Mockito.lenient().when(currencyRateService.convertToBaseCurrency(anyDouble(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Double.class));
    }

    @Test
    void evaluate_firesWhenSymbolExceedsThreshold() {
        when(openedPositionRepository.findAll()).thenReturn(List.of(
                position("AAPL.US", 10.0, 100.0),   // 1000
                position("MSFT.US", 1.0, 100.0),    // 100
                position("PG.US", 1.0, 100.0)       // 100
        ));

        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
        // AAPL is ~83% of total -> must trigger.
        assertTrue(result.get().contains("AAPL.US"));
    }

    @Test
    void evaluate_isQuietForBalancedPortfolio() {
        when(openedPositionRepository.findAll()).thenReturn(List.of(
                position("AAPL.US", 1.0, 100.0),
                position("MSFT.US", 1.0, 100.0),
                position("PG.US", 1.0, 100.0),
                position("KO.US", 1.0, 100.0),
                position("LMT.US", 1.0, 100.0)
        ));

        assertFalse(rule.evaluate().isPresent());
    }

    @Test
    void evaluate_isSafeWhenPortfolioIsEmpty() {
        when(openedPositionRepository.findAll()).thenReturn(List.of());

        assertFalse(rule.evaluate().isPresent());
    }

    private static OpenedPosition position(String symbol, double volume, double price) {
        OpenedPosition p = new OpenedPosition();
        p.setSymbol(symbol);
        p.setCurrency(CurrencyType.USD);
        p.setVolume(volume);
        p.setMarketPrice(price);
        return p;
    }
}

