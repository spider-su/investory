package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import java.math.BigDecimal;

/** Public bucket result used by the canonical simulation timeline model. */
public record BucketResult(
    EconomicBucket bucket,
    BigDecimal startValue,
    BigDecimal returnAmount,
    BigDecimal refill,
    BigDecimal withdrawal,
    BigDecimal expectedEndValue) {
  /** Signed net internal transfer. Kept separate from the legacy component name. */
  public BigDecimal transfer() {
    return refill;
  }
}
