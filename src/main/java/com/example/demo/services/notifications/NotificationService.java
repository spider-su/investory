package com.example.demo.services.notifications;

import com.example.demo.controllers.bot.PortfolioBot;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Aggregates and dispatches Telegram notifications:
 * - {@link #sendDailyDigest()} pushes a snapshot summary after the market-close job.
 * - {@link #runAlerts()} evaluates every {@link AlertRule} and sends only fired ones.
 *
 * Telegram is optional: when {@code app.telegram.enabled=false} the {@link PortfolioBot}
 * bean is absent and messages are logged instead of being sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectProvider<PortfolioBot> botProvider;
    private final PortfolioService portfolioService;
    private final List<AlertRule> alertRules;
    private final NotificationProperties properties;

    public void sendDailyDigest() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            Portfolio p = portfolioService.calculateTotalProfitLoss();
            String message = buildDigest(p);
            send(message);
        } catch (Exception e) {
            log.warn("Failed to build/send daily digest", e);
        }
    }

    public void runAlerts() {
        if (!properties.isEnabled()) {
            return;
        }
        for (AlertRule rule : alertRules) {
            try {
                rule.evaluate().ifPresent(message -> {
                    log.info("Alert fired: {}", rule.code());
                    send("\u26A0\uFE0F " + message);
                });
            } catch (Exception e) {
                log.warn("Alert rule {} failed", rule.code(), e);
            }
        }
    }

    private String buildDigest(Portfolio p) {
        return String.format(
                "\uD83D\uDCCA Daily digest%n" +
                        "Balance: %s %s%n" +
                        "Total P/L: %s %s (unrealized %s, realized %s)%n" +
                        "Dividends: %s %s%n" +
                        "Cap-gains tax (est): %s %s",
                fmt(p.getBalance()), p.getBaseCurrency(),
                fmt(p.getTotal()), p.getBaseCurrency(),
                fmt(p.getTotalUnrealizedInBase()), fmt(p.getTotalProfitInBase()),
                fmt(p.getDividends()), p.getBaseCurrency(),
                fmt(p.getCapitalGainsTax()), p.getBaseCurrency()
        );
    }

    private static String fmt(double value) {
        return String.format("%,.0f", value);
    }

    private void send(String message) {
        PortfolioBot bot = botProvider.getIfAvailable();
        if (bot == null) {
            log.info("[notification] {}", message.replace('\n', ' '));
            return;
        }
        bot.sendMessage(message);
    }
}

