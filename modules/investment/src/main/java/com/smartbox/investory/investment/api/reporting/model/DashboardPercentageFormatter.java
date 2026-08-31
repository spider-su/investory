package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Shared normal presentation for user-facing percentage values. */
public final class DashboardPercentageFormatter {
  private DashboardPercentageFormatter() {}

  public static String percent(Double value) {
    return percent(value, false);
  }

  public static String percent(BigDecimal value) {
    return value == null ? "-" : percent(value.doubleValue(), false);
  }

  public static String signedPercent(Double value) {
    return percent(value, true);
  }

  public static String percentagePoints(Double value) {
    if (value == null) return "-";
    return signedPercent(value).replace("%", "") + " pp";
  }

  private static String percent(Double value, boolean sign) {
    if (value == null) return "-";
    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
    formatter.setRoundingMode(RoundingMode.HALF_UP);
    formatter.setMinimumFractionDigits(1);
    formatter.setMaximumFractionDigits(1);
    return (sign && value >= 0 ? "+" : "") + formatter.format(value) + "%";
  }
}
