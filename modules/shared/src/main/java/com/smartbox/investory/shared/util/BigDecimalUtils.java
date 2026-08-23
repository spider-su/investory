package com.smartbox.investory.shared.util;

import java.math.BigDecimal;

public final class BigDecimalUtils {
  private BigDecimalUtils() {}

  public static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
