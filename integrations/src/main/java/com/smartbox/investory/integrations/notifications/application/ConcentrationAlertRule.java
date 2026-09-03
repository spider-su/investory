package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.PortfolioExposureReader;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
    List<AlertObservation> observations = evaluateObservations();
    if (observations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "Concentration alert (>= "
            + properties.getConcentrationThresholdPct()
            + "% of portfolio):\n"
            + observations.stream()
                .map(AlertObservation::message)
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow());
  }

  @Override
  public List<AlertObservation> evaluateObservations() {
    Map<String, Double> exposureBySymbol = new TreeMap<>();
    double total = 0.0;
    for (var exposure : investment.symbolExposures()) {
      double base = exposure.value().doubleValue();
      exposureBySymbol.merge(exposure.symbol(), base, Double::sum);
      total += base;
    }
    if (total <= 0.0) {
      return List.of();
    }
    double threshold = properties.getConcentrationThresholdPct();
    List<AlertObservation> observations = new java.util.ArrayList<>();
    for (Map.Entry<String, Double> e : exposureBySymbol.entrySet()) {
      double pct = e.getValue() / total * 100.0;
      if (pct >= threshold) {
        observations.add(
            new AlertObservation(
                e.getKey(),
                String.format("%s: %.1f%% (%,.0f %s)", e.getKey(), pct, e.getValue(), BASE)));
      }
    }
    return observations;
  }
}
