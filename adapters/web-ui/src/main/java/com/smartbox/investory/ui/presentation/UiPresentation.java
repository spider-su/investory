package com.smartbox.investory.ui.presentation;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.shared.presentation.FinancialPrecision;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;

/** Stable display formatting and labels for the planning pages. */
public final class UiPresentation {
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm z", Locale.ENGLISH);

  public UiPresentation() {}

  public static String date(LocalDate value) {
    return value == null ? "—" : DATE_FORMAT.format(value);
  }

  public static String dateTime(ZonedDateTime value) {
    return value == null ? "—" : DATE_TIME_FORMAT.format(value);
  }

  public static String dateTime(OffsetDateTime value) {
    return value == null
        ? "—"
        : DATE_TIME_FORMAT.format(value.atZoneSameInstant(ZoneId.systemDefault()));
  }

  public static String dateTime(Instant value) {
    return value == null ? "—" : DATE_TIME_FORMAT.format(value.atZone(ZoneId.systemDefault()));
  }

  public static String money(BigDecimal value) {
    return FinancialPresentation.money(value);
  }

  public static String moneyWhole(BigDecimal value) {
    return FinancialPresentation.moneyWhole(value);
  }

  public static String wholeNumber(BigDecimal value) {
    return FinancialPresentation.wholeNumber(value);
  }

  /** Formats monetary planning values in thousands with one decimal place. */
  public static String thousands(BigDecimal value) {
    BigDecimal amount = zeroIfNull(value);
    return number(amount.divide(BigDecimal.valueOf(1000), RoundingMode.HALF_UP), 1, 1);
  }

  /** Compact summary money, with a locale-stable K/M suffix and no scientific notation. */
  public static String compactMoney(BigDecimal value) {
    return FinancialPresentation.compactMoney(value);
  }

  public static String compactMoneyTrimmed(BigDecimal value) {
    return FinancialPresentation.compactMoneyTrimmed(value);
  }

  /** Compact money with an explicit sign for internal transfers and net changes. */
  public static String signedCompactMoney(BigDecimal value) {
    BigDecimal amount = zeroIfNull(value);
    if (amount.signum() > 0) return "+" + compactMoney(amount);
    if (amount.signum() < 0) return "−" + compactMoney(amount.abs());
    return compactMoney(amount);
  }

  /** Formats an already grouped UI amount, for example {@code "213,684"}. */
  public static String thousands(String value) {
    return thousands(
        value == null || value.isBlank()
            ? BigDecimal.ZERO
            : new BigDecimal(value.replace(",", "")));
  }

  /** Natural, unitless decimal display for values such as reserve coverage years. */
  public static String decimal(BigDecimal value) {
    return FinancialPresentation.decimal(value);
  }

  /** Formats planning durations with one decimal place for coverage/runway values. */
  public static String years(BigDecimal value) {
    return number(zeroIfNull(value), 0, 1);
  }

  /** Browser-safe unitless decimal form value. */
  public static String decimalInput(BigDecimal value) {
    return plain(value, 2);
  }

  /** Browser-safe numeric form value: no grouping and no database-scale noise. */
  public static String moneyInput(BigDecimal value) {
    return plain(value, 2);
  }

  /** Browser-safe whole monetary input, retaining blank optional values. */
  public static String wholeMoneyInput(BigDecimal value) {
    return value == null
        ? ""
        : value.setScale(0, FinancialPrecision.REPORTING_ROUNDING).toPlainString();
  }

  /** Monthly total for all rental income terms, including parking and other income. */
  public static BigDecimal monthlyIncome(
      Collection<com.smartbox.investory.longterm.api.model.RentalTermView> terms) {
    if (terms == null) return BigDecimal.ZERO;
    return terms.stream()
        .filter(
            term ->
                term.type() == CashFlowType.RENT
                    || term.type() == CashFlowType.PARKING_RENT
                    || term.type() == CashFlowType.OTHER_INCOME)
        .map(
            term ->
                term.frequency() == Frequency.MONTHLY
                    ? term.amount()
                    : term.amount().divide(BigDecimal.valueOf(12), 18, RoundingMode.HALF_UP))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Browser-safe percentage-point input, for example 0.075 becomes 7.5. */
  public static String percentageInput(BigDecimal ratio) {
    BigDecimal percentagePoints =
        zeroIfNull(ratio).multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
    return (percentagePoints.scale() < 1
            ? percentagePoints.setScale(1, RoundingMode.UNNECESSARY)
            : percentagePoints)
        .toPlainString();
  }

  /**
   * Plain whole number for a numeric HTML input; display values use {@link
   * #wholeNumber(BigDecimal)}.
   */
  public static String wholeNumberInput(BigDecimal value) {
    return zeroIfNull(value).setScale(0, FinancialPrecision.REPORTING_ROUNDING).toPlainString();
  }

  public static String moneyWhole(BigDecimal value, Object currency) {
    return FinancialPresentation.moneyWhole(value, currency);
  }

  public static String money(BigDecimal value, Object currency) {
    return FinancialPresentation.money(value, currency);
  }

  public static String percentage(BigDecimal ratio) {
    return FinancialPresentation.percentage(ratio);
  }

  public static String signedPercentage(BigDecimal ratio) {
    BigDecimal value = zeroIfNull(ratio);
    return (value.signum() > 0 ? "+" : "") + percentage(value);
  }

  /** Formats a signed percentage-point delta with one decimal place. */
  public static String percentagePoints(BigDecimal value) {
    BigDecimal points = zeroIfNull(value);
    return (points.signum() > 0 ? "+" : "") + number(points, 1, 1) + " pp";
  }

  public static String rate(BigDecimal ratio) {
    return FinancialPresentation.rate(ratio);
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

  /** CSS token for the investment dashboard allocation bar. */
  public static String investmentAllocationCssKey(String label) {
    if (label == null) return "other";
    return switch (label.trim().toLowerCase(Locale.ROOT)) {
      case "cash", "liquid cash" -> "cash";
      case "fixed income", "bonds" -> "fixed-income";
      case "equity", "stocks" -> "equity";
      case "real estate" -> "real-estate";
      default -> "other";
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
    if (value == null) return "—";
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

  /** Formats the stable Long-Term public API frequency used by current templates. */
  public static String frequency(Frequency value) {
    return title(value);
  }

  private static String number(BigDecimal value, int minimumScale, int maximumScale) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(minimumScale);
    format.setMaximumFractionDigits(maximumScale);
    return format.format(zeroIfNull(value));
  }

  private static String plain(BigDecimal value, int scale) {
    return zeroIfNull(value)
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
