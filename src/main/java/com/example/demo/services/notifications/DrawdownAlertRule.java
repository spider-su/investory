package com.example.demo.services.notifications;

import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
/**
 * Fires when current total P/L vs all-time peak drops by more than the configured percentage.
 * The "peak" here is approximated from the running balance (deposits net of withdrawals + total P/L).
 */
@Component
@RequiredArgsConstructor
public class DrawdownAlertRule implements AlertRule {

    private final PortfolioService portfolioService;
    private final NotificationProperties properties;

    private double peakEquity = 0.0;

    @Override
    public String code() {
        return "DRAWDOWN";
    }

    @Override
    public Optional<String> evaluate() {
        Portfolio p = portfolioService.calculateTotalProfitLoss();
        double equity = p.getBalance();
        if (equity > peakEquity) {
            peakEquity = equity;
            return Optional.empty();
        }
        if (peakEquity <= 0.0) {
            return Optional.empty();
        }
        double drawdownPct = (peakEquity - equity) / peakEquity * 100.0;
        if (drawdownPct >= properties.getDrawdownThresholdPct()) {
            return Optional.of(String.format(
                    "Drawdown alert: %.1f%% below peak (peak %,.0f %s, now %,.0f %s)",
                    drawdownPct, peakEquity, p.getBaseCurrency(), equity, p.getBaseCurrency()));
        }
        return Optional.empty();
    }
}


