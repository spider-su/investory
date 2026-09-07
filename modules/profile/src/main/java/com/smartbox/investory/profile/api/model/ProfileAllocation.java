package com.smartbox.investory.profile.api.model;

import java.math.BigDecimal;
import java.util.Objects;

public record ProfileAllocation(
    EconomicBucket bucket,
    BigDecimal value,
    BigDecimal percentage,
    Liquidity liquidity,
    AssetHorizon assetHorizon) {
  public ProfileAllocation {
    Objects.requireNonNull(bucket, "bucket");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(percentage, "percentage");
    Objects.requireNonNull(liquidity, "liquidity");
    Objects.requireNonNull(assetHorizon, "assetHorizon");
  }

  public boolean isNonZero() {
    return value != null && value.signum() != 0;
  }
}
