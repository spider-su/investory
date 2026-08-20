package com.smartbox.investory.investment.reporting.dashboard.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

public enum DashboardPeriod {
  ONE_MONTH("1M", "1M"),
  THREE_MONTHS("3M", "3M"),
  SIX_MONTHS("6M", "6M"),
  YEAR_TO_DATE("YTD", "YTD"),
  ONE_YEAR("1Y", "1Y"),
  THREE_YEARS("3Y", "3Y"),
  FIVE_YEARS("5Y", "5Y"),
  MAX("MAX", "Max");

  private final String urlValue;
  private final String label;

  DashboardPeriod(String urlValue, String label) {
    this.urlValue = urlValue;
    this.label = label;
  }

  public String urlValue() {
    return urlValue;
  }

  public String label() {
    return label;
  }

  public ZonedDateTime startDate(ZonedDateTime now) {
    return switch (this) {
      case ONE_MONTH -> now.minusMonths(1);
      case THREE_MONTHS -> now.minusMonths(3);
      case SIX_MONTHS -> now.minusMonths(6);
      case YEAR_TO_DATE -> LocalDate.of(now.getYear(), 1, 1).atStartOfDay(ZoneId.from(now));
      case ONE_YEAR -> now.minusYears(1);
      case THREE_YEARS -> now.minusYears(3);
      case FIVE_YEARS -> now.minusYears(5);
      case MAX -> null;
    };
  }

  public static DashboardPeriod fromUrlValue(String value) {
    if (value == null || value.isBlank()) {
      return YEAR_TO_DATE;
    }
    return Arrays.stream(values())
        .filter(period -> period.urlValue.equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElse(YEAR_TO_DATE);
  }
}
