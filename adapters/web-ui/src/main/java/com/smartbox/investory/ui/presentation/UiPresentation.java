package com.smartbox.investory.ui.presentation;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.planning.PlanningMetric;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.simulation.SimulationFundingStrategy;
import com.smartbox.investory.shared.presentation.FinancialPrecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Stable display formatting and labels for the planning pages. */
public final class UiPresentation {
  public UiPresentation() {}

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

  /** Natural, unitless decimal display for values such as reserve coverage years. */
  public static String decimal(BigDecimal value) {
    return money(value);
  }

  /** Formats planning durations with one decimal place for coverage/runway values. */
  public static String years(BigDecimal value) {
    return number(value == null ? BigDecimal.ZERO : value, 1, 1);
  }

  /** Browser-safe unitless decimal form value. */
  public static String decimalInput(BigDecimal value) {
    return plain(value, 2);
  }

  /** Browser-safe numeric form value: no grouping and no database-scale noise. */
  public static String moneyInput(BigDecimal value) {
    return plain(value, 2);
  }

  /** Browser-safe percentage-point input, for example 0.075 becomes 7.5. */
  public static String percentageInput(BigDecimal ratio) {
    return (ratio == null ? BigDecimal.ZERO : ratio.multiply(BigDecimal.valueOf(100)))
        .setScale(1, FinancialPrecision.REPORTING_ROUNDING)
        .toPlainString();
  }

  /**
   * Plain whole number for a numeric HTML input; display values use {@link
   * #wholeNumber(BigDecimal)}.
   */
  public static String wholeNumberInput(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value)
        .setScale(0, FinancialPrecision.REPORTING_ROUNDING)
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

  /** Formats a value by its declared planning metric unit after currency conversion, if any. */
  public static String planningMetric(PlanningMetric metric, BigDecimal value) {
    return switch (metric.presentationType()) {
      case MONEY -> money(value);
      case PERCENTAGE -> percentage(value);
      case NUMBER -> decimal(value);
    };
  }

  /**
   * Formats a value by its declared planning metric unit with an explicit currency suffix where
   * required.
   */
  public static String planningMetric(PlanningMetric metric, BigDecimal value, Object currency) {
    return switch (metric.presentationType()) {
      case MONEY -> money(value, currency);
      case PERCENTAGE -> percentage(value);
      case NUMBER -> decimal(value);
    };
  }

  public static String bucket(EconomicBucket bucket) {
    return switch (bucket) {
      case LIQUID_CASH -> "Cash";
      case FIXED_INCOME -> "Fixed income";
      case EQUITY -> "Equity";
      case REAL_ESTATE -> "Real estate";
      case OTHER -> "Other";
    };
  }

  public static String liquidity(Liquidity value) {
    return value == Liquidity.ILLIQUID ? "Illiquid" : "Liquid";
  }

  public static String fundingStrategy(SimulationFundingStrategy value) {
    return switch (value) {
      case SIMPLE_WATERFALL -> "Simple waterfall";
      case RESERVE_AND_HARVEST -> "Reserve + equity harvest";
    };
  }

  public static String assetType(LongTermAssetType value) {
    return switch (value) {
      case REAL_ESTATE -> "Real estate";
      case BOND -> "Bond";
      case DEPOSIT -> "Deposit";
      case CASH_RESERVE -> "Cash reserve";
      case OTHER -> "Other";
    };
  }

  public static String interestTreatment(InterestTreatment value) {
    return value == InterestTreatment.PAY_OUT ? "Distributed" : "Accumulative";
  }

  public static String cashFlowType(CashFlowType value) {
    return switch (value) {
      case RENT -> "Rent";
      case PARKING_RENT -> "Parking rent";
      case OTHER_INCOME -> "Other income";
      case ADMIN_FEE -> "Administration";
      case UTILITIES -> "Utilities";
      case PROPERTY_TAX -> "Property tax";
      case INSURANCE -> "Insurance";
      case OTHER_EXPENSE -> "Other expense";
    };
  }

  public static String frequency(Frequency value) {
    return title(value);
  }

  private static String number(BigDecimal value, int minimumScale, int maximumScale) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(minimumScale);
    format.setMaximumFractionDigits(maximumScale);
    return format.format(value == null ? BigDecimal.ZERO : value);
  }

  private static String plain(BigDecimal value, int scale) {
    return (value == null ? BigDecimal.ZERO : value)
        .setScale(scale, FinancialPrecision.REPORTING_ROUNDING)
        .stripTrailingZeros()
        .toPlainString();
  }

  private static String title(Enum<?> value) {
    if (value == null) return "—";
    String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
