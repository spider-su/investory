package com.smartbox.investory.integration.notifications;

import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.market.fx.currency.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.HashMap;
import java.util.List;
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

  private final OpenedPositionRepository openedPositionRepository;
  private final CurrencyRateService currencyRateService;
  private final NotificationProperties properties;

  @Override
  public String code() {
    return "CONCENTRATION";
  }

  @Override
  public Optional<String> evaluate() {
    Map<String, Double> exposureBySymbol = new HashMap<>();
    double total = 0.0;
    List<OpenedPosition> positions = openedPositionRepository.findAll();
    for (OpenedPosition p : positions) {
      if (p.getSymbol() == null) {
        continue;
      }
      double volume = p.signedQuantity();
      double price =
          p.getMarketPrice() != null
              ? p.getMarketPrice()
              : (p.getOpenPrice() != null ? p.getOpenPrice() : 0.0);
      double native_ = Math.abs(volume * price);
      double base = currencyRateService.convertToBaseCurrency(native_, BASE, p.getPriceCurrency());
      exposureBySymbol.merge(p.getSymbol(), base, Double::sum);
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
