package com.smartbox.investory.notifications;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.notifications.radar")
record RadarNotificationSettings(
    @DefaultValue("3") double materialWeightPct,
    @DefaultValue("2") int confirmationSessions,
    @DefaultValue("90") int minimumCoveragePct,
    @DefaultValue("20m") java.time.Duration scanGrace,
    @DefaultValue("15m") java.time.Duration themeGrace,
    @DefaultValue("DIS") List<String> watchSymbols,
    @DefaultValue("Defense") List<String> watchThemes,
    Map<String, List<String>> themeHoldings,
    Map<String, AllocationBand> allocationBands,
    @DefaultValue Reserve reserve) {

  RadarNotificationSettings {
    if (!validWeight(materialWeightPct)
        || materialWeightPct <= 0
        || confirmationSessions < 2
        || confirmationSessions > 10
        || minimumCoveragePct < 1
        || minimumCoveragePct > 100
        || scanGrace.isNegative()
        || themeGrace.isNegative()) {
      throw new IllegalArgumentException("Invalid Radar notification thresholds");
    }
    watchSymbols = List.copyOf(watchSymbols);
    watchThemes = List.copyOf(watchThemes);
    themeHoldings = themeHoldings == null ? Map.of() : Map.copyOf(themeHoldings);
    allocationBands = allocationBands == null ? Map.of() : Map.copyOf(allocationBands);
  }

  record Reserve(BigDecimal floor, CurrencyType currency, BigDecimal commitments) {
    Reserve {
      if ((floor == null) != (currency == null)
          || (floor != null && floor.signum() <= 0)
          || (commitments != null && commitments.signum() < 0)) {
        throw new IllegalArgumentException("Reserve needs a positive floor and display currency");
      }
    }
  }

  record AllocationBand(double minimumPct, double maximumPct, List<String> symbols) {
    AllocationBand {
      if (!validWeight(minimumPct)
          || !validWeight(maximumPct)
          || minimumPct >= maximumPct
          || symbols == null
          || symbols.isEmpty()) {
        throw new IllegalArgumentException(
            "Allocation band needs symbols and increasing weight bounds");
      }
      symbols = List.copyOf(symbols);
    }
  }

  private static boolean validWeight(double value) {
    return Double.isFinite(value) && value >= 0 && value <= 100;
  }
}
