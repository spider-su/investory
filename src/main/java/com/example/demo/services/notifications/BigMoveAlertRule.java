package com.example.demo.services.notifications;

import com.example.demo.infrastructure.repository.Stock;
import com.example.demo.infrastructure.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * Fires when any tracked stock's intraday move (dayOpenPrice -> marketPrice) exceeds threshold.
 */
@Component
@RequiredArgsConstructor
public class BigMoveAlertRule implements AlertRule {

    private final StockRepository stockRepository;
    private final NotificationProperties properties;

    @Override
    public String code() {
        return "BIG_MOVE";
    }

    @Override
    public Optional<String> evaluate() {
        double threshold = properties.getBigMoveThresholdPct();
        List<String> hits = new ArrayList<>();
        for (Stock s : stockRepository.findAll()) {
            Double open = s.getDayOpenPrice();
            Double price = s.getMarketPrice();
            if (open == null || price == null || open == 0.0) {
                continue;
            }
            double pct = (price - open) / open * 100.0;
            if (Math.abs(pct) >= threshold) {
                hits.add(String.format("%s %+.2f%% (%.2f -> %.2f)",
                        s.getSymbol(), pct, open, price));
            }
        }
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Big intraday moves (>= " + threshold + "%):\n" + String.join("\n", hits));
    }
}


