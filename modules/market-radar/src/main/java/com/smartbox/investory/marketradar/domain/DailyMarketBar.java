package com.smartbox.investory.marketradar.domain;

import java.time.LocalDate;

public record DailyMarketBar(
    LocalDate date, double open, double high, double low, double close, long volume) {

  public DailyMarketBar {
    if (date == null || !Double.isFinite(close) || close <= 0 || volume < 0) {
      throw new IllegalArgumentException("Invalid daily market bar");
    }
  }
}
