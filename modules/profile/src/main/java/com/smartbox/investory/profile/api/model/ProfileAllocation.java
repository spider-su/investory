package com.smartbox.investory.profile.api.model;

import java.math.BigDecimal;

public record ProfileAllocation(
    EconomicBucket bucket,
    BigDecimal value,
    BigDecimal percentage,
    Liquidity liquidity,
    AssetHorizon assetHorizon) {
  public boolean isNonZero() {
    return value != null && value.signum() != 0;
  }
}
