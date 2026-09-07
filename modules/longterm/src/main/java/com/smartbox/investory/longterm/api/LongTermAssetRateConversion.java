package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;

/** Converts percentage-point input at the REST/UI boundary to canonical decimal rates. */
public final class LongTermAssetRateConversion {
  private LongTermAssetRateConversion() {}

  public static BigDecimal percentToRate(BigDecimal percent) {
    return percent == null ? null : percent.movePointLeft(2);
  }

  public static BigDecimal rateToPercent(BigDecimal rate) {
    return rate == null ? null : rate.movePointRight(2);
  }
}
