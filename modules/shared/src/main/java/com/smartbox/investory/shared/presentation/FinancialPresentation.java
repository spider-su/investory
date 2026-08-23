package com.smartbox.investory.shared.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Domain-neutral formatting used by domain-owned presentation models. */
public final class FinancialPresentation {
  private FinancialPresentation() {}

  public static String money(BigDecimal value) {
    BigDecimal rounded =
        (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    return rounded.stripTrailingZeros().scale() <= 0
        ? number(rounded, 0, 0)
        : number(rounded, 2, 2);
  }

  public static String moneyWhole(BigDecimal value) {
    return number(value, 0, 0);
  }

  public static String wholeNumber(BigDecimal value) {
    return number(value, 0, 0);
  }

  /** Compact summary money, with stable K/M suffixes. */
  public static String compactMoney(BigDecimal value) {
    BigDecimal amount = value == null ? BigDecimal.ZERO : value;
    BigDecimal absolute = amount.abs();
    if (absolute.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
      return number(amount.divide(BigDecimal.valueOf(1_000_000)), 0, 2) + "M";
    }
    if (absolute.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
      BigDecimal thousands = amount.divide(BigDecimal.valueOf(1_000)).setScale(1, RoundingMode.HALF_UP);
      if (thousands.abs().compareTo(BigDecimal.valueOf(1_000)) >= 0) {
        return number(amount.divide(BigDecimal.valueOf(1_000_000)), 0, 2) + "M";
      }
      return number(thousands, 1, 1) + "K";
    }
    return number(amount, 0, 0);
  }

  public static String decimal(BigDecimal value) {
    return money(value);
  }

  public static String years(BigDecimal value) {
    return number(value == null ? BigDecimal.ZERO : value, 1, 1);
  }

  public static String decimalInput(BigDecimal value) {
    return plain(value, 2);
  }

  public static String moneyInput(BigDecimal value) {
    return plain(value, 2);
  }

  public static String percentageInput(BigDecimal ratio) {
    return (ratio == null ? BigDecimal.ZERO : ratio.multiply(BigDecimal.valueOf(100)))
        .setScale(1, RoundingMode.HALF_UP)
        .toPlainString();
  }

  public static String wholeNumberInput(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value)
        .setScale(0, RoundingMode.HALF_UP)
        .toPlainString();
  }

  public static String moneyWhole(BigDecimal value, Object currency) {
    return moneyWhole(value) + (currency == null ? "" : " " + currency);
  }

  public static String money(BigDecimal value, Object currency) {
    return money(value) + (currency == null ? "" : " " + currency);
  }

  public static String percentage(BigDecimal ratio) {
    return number(ratio == null ? BigDecimal.ZERO : ratio.multiply(BigDecimal.valueOf(100)), 1, 1)
        + "%";
  }

  public static String rate(BigDecimal ratio) {
    return percentage(ratio);
  }

  public static BigDecimal planningDifference(BigDecimal actual, BigDecimal planned) {
    return actual == null || planned == null ? null : actual.subtract(planned);
  }

  private static String number(BigDecimal value, int minimumScale, int maximumScale) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(minimumScale);
    format.setMaximumFractionDigits(maximumScale);
    return format.format(value == null ? BigDecimal.ZERO : value);
  }

  private static String plain(BigDecimal value, int scale) {
    return (value == null ? BigDecimal.ZERO : value)
        .setScale(scale, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString();
  }
}
