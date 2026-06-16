package com.example.demo.services.notifications;

import com.example.demo.controllers.bot.PortfolioBot;
import com.example.demo.data.CurrencyType;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ObjectProvider<PortfolioBot> botProvider;
    @Mock
    private PortfolioBot bot;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private AlertRule firingRule;
    @Mock
    private AlertRule silentRule;
    @Mock
    private AlertRule throwingRule;

    private NotificationProperties properties;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setEnabled(true);
    }

    @Test
    void sendDailyDigest_skipsWhenNotificationsDisabled() {
        properties.setEnabled(false);
        service = new NotificationService(botProvider, portfolioService, List.of(), properties);

        service.sendDailyDigest();

        verifyNoInteractions(portfolioService, botProvider, bot);
    }

    @Test
    void sendDailyDigest_buildsAndSendsMessageWhenBotAvailable() {
        Portfolio portfolio = new Portfolio();
        portfolio.setBaseCurrency(CurrencyType.USD);
        portfolio.setBalance(12345.0);
        portfolio.setTotal(678.0);
        portfolio.setTotalUnrealizedInBase(100.0);
        portfolio.setTotalProfitInBase(578.0);
        portfolio.setDividends(50.0);
        portfolio.setCapitalGainsTax(12.5);
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
        when(botProvider.getIfAvailable()).thenReturn(bot);

        service = new NotificationService(botProvider, portfolioService, List.of(), properties);
        service.sendDailyDigest();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMessage(messageCaptor.capture());
        String message = messageCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("Daily digest"));
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("12,345"));
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("USD"));
    }

    @Test
    void sendDailyDigest_logsWhenBotUnavailable() {
        Portfolio portfolio = new Portfolio();
        portfolio.setBaseCurrency(CurrencyType.USD);
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
        when(botProvider.getIfAvailable()).thenReturn(null);

        service = new NotificationService(botProvider, portfolioService, List.of(), properties);
        // Just assert it does not throw and does not call bot.sendMessage (no bot was returned).
        service.sendDailyDigest();
    }

    @Test
    void sendDailyDigest_swallowsExceptions() {
        when(portfolioService.calculateTotalProfitLoss()).thenThrow(new RuntimeException("boom"));
        service = new NotificationService(botProvider, portfolioService, List.of(), properties);

        // Must not propagate; scheduler keeps running.
        service.sendDailyDigest();
        verify(bot, never()).sendMessage(anyString());
    }

    @Test
    void runAlerts_sendsOnlyFiredRulesAndContinuesAfterRuleFailure() {
        // firingRule and throwingRule both need a code() — it's logged. silentRule never fires
        // so its code() is unused; stub it lenient to avoid strict-stubbing complaints.
        org.mockito.Mockito.lenient().when(firingRule.code()).thenReturn("FIRING");
        when(firingRule.evaluate()).thenReturn(Optional.of("warning text"));
        when(silentRule.evaluate()).thenReturn(Optional.empty());
        when(throwingRule.code()).thenReturn("BROKEN");
        when(throwingRule.evaluate()).thenThrow(new RuntimeException("rule broke"));
        when(botProvider.getIfAvailable()).thenReturn(bot);

        service = new NotificationService(botProvider, portfolioService,
                List.of(firingRule, silentRule, throwingRule), properties);
        service.runAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot, times(1)).sendMessage(messageCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(messageCaptor.getValue().contains("warning text"));
    }

    @Test
    void runAlerts_skipsWhenDisabled() {
        properties.setEnabled(false);
        service = new NotificationService(botProvider, portfolioService, List.of(firingRule), properties);

        service.runAlerts();

        verifyNoInteractions(firingRule);
    }
}

