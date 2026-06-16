package com.example.demo.services.notifications;

import com.example.demo.data.CurrencyType;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrawdownAlertRuleTest {

    @Mock
    private PortfolioService portfolioService;

    private NotificationProperties properties;
    private DrawdownAlertRule rule;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setDrawdownThresholdPct(10.0);
        rule = new DrawdownAlertRule(portfolioService, properties);
    }

    @Test
    void code_isStable() {
        org.junit.jupiter.api.Assertions.assertEquals("DRAWDOWN", rule.code());
    }

    @Test
    void evaluate_isQuietBeforePeakIsEstablished() {
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolioWithBalance(1000.0));

        Optional<String> result = rule.evaluate();

        assertFalse(result.isPresent());
    }

    @Test
    void evaluate_firesWhenBalanceDropsBelowThreshold() {
        when(portfolioService.calculateTotalProfitLoss())
                .thenReturn(portfolioWithBalance(1000.0))   // peak
                .thenReturn(portfolioWithBalance(850.0));   // -15%

        rule.evaluate(); // records peak
        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("15"));
    }

    @Test
    void evaluate_doesNotFireForSmallDrop() {
        when(portfolioService.calculateTotalProfitLoss())
                .thenReturn(portfolioWithBalance(1000.0))
                .thenReturn(portfolioWithBalance(950.0));   // -5%

        rule.evaluate();
        Optional<String> result = rule.evaluate();

        assertFalse(result.isPresent());
    }

    @Test
    void evaluate_updatesPeakUpward() {
        when(portfolioService.calculateTotalProfitLoss())
                .thenReturn(portfolioWithBalance(1000.0))
                .thenReturn(portfolioWithBalance(1200.0))
                .thenReturn(portfolioWithBalance(1080.0)); // -10% from new peak

        rule.evaluate();
        rule.evaluate(); // raises peak to 1200
        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
    }

    private static Portfolio portfolioWithBalance(double balance) {
        Portfolio p = new Portfolio();
        p.setBaseCurrency(CurrencyType.USD);
        p.setBalance(balance);
        return p;
    }
}

