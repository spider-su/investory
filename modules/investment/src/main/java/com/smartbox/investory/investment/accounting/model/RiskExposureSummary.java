package com.smartbox.investory.investment.accounting.model;

import java.util.List;

public record RiskExposureSummary(
    Double largestAssetWeightPct,
    Double topFiveAssetConcentrationPct,
    Double baseCurrencyAccountExposurePct,
    Double foreignCurrencyAccountExposurePct,
    double availableCash,
    double incomeSinceInception,
    String periodLabel,
    List<String> warnings) {
  public static RiskExposureSummary unavailable(double cash) {
    return new RiskExposureSummary(
        null,
        null,
        null,
        null,
        cash,
        0.0,
        "Current snapshot",
        List.of("Exposure data unavailable"));
  }
}
