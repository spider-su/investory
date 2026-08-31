package com.smartbox.investory.investment.api.reporting.model;

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
    warnings = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(warnings);
  }
}
