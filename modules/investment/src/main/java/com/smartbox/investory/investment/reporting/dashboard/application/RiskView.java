package com.smartbox.investory.investment.reporting.dashboard.application;

import java.util.List;

public record RiskView(
    Double largestAssetWeightPct,
    Double topFiveAssetConcentrationPct,
    Double baseCurrencyAccountExposurePct,
    Double foreignCurrencyAccountExposurePct,
    double availableCash,
    double incomeSinceInception,
    String concentrationWarning,
    String periodLabel,
    List<String> warnings) {

  public RiskView {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
