package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;

public record ReserveState(
    BigDecimal startValue,
    BigDecimal withdrawal,
    BigDecimal reviewAdjustment,
    BigDecimal endValue,
    ProjectionSource source) {
  public ReserveState {
    startValue = zeroIfNull(startValue);
    reviewAdjustment = zeroIfNull(reviewAdjustment);
    withdrawal =
        zeroIfNull(withdrawal)
            .max(BigDecimal.ZERO)
            .min(startValue.add(reviewAdjustment).max(BigDecimal.ZERO));
    endValue = startValue.add(reviewAdjustment).subtract(withdrawal).max(BigDecimal.ZERO);
    source = source == null ? ProjectionSource.PROJECTED : source;
  }
}
