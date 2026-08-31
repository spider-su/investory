package com.smartbox.investory.integrations.notifications;

import com.smartbox.investory.investment.api.operations.PortfolioExposureReader;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fires when a single symbol exceeds the configured percentage of total open market value (in base
 * currency).
 */
@Component
@RequiredArgsConstructor
public class ConcentrationAlertRule implements AlertRule {

  private static final CurrencyType BASE = CurrencyType.USD;

  private final PortfolioExposureReader investment;
  private final NotificationProperties properties;

  @Override
  public String code() {
    return "CONCENTRATION";
  }

  @Override
  public Optional<String> evaluate() {
    Map<String, Double> exposureBySymbol = new HashMap<>();
    double total = 0.0;
    for (var exposure : investment.symbolExposures()) {
      double base = exposure.value().doubleValue();
      exposureBySymbol.merge(exposure.symbol(), base, Double::sum);
      total += base;
    }
    if (total <= 0.0) {
      return Optional.empty();
    }
    double threshold = properties.getConcentrationThresholdPct();
    StringBuilder sb = null;
    for (Map.Entry<String, Double> e : exposureBySymbol.entrySet()) {
      double pct = e.getValue() / total * 100.0;
      if (pct >= threshold) {
        if (sb == null) {
          sb =
              new StringBuilder("Concentration alert (>= ")
                  .append(threshold)
                  .append("% of portfolio):\n");
        }
        sb.append(String.format("%s: %.1f%% (%,.0f %s)%n", e.getKey(), pct, e.getValue(), BASE));
      }
    }
    return sb == null ? Optional.empty() : Optional.of(sb.toString().trim());
  }
}
