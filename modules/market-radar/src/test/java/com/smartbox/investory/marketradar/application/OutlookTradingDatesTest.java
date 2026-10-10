package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OutlookTradingDatesTest {
  @Test
  void yearEndAndHolidayPolicies() {
    assertThat(OutlookTradingDates.previousOrSame(LocalDate.of(2023, 12, 31)))
        .isEqualTo(LocalDate.of(2023, 12, 29));
    assertThat(OutlookTradingDates.nextOrSame(LocalDate.of(2023, 12, 31)))
        .isEqualTo(LocalDate.of(2024, 1, 2));
    assertThat(OutlookTradingDates.nextOrSame(LocalDate.of(2026, 4, 3)))
        .isEqualTo(LocalDate.of(2026, 4, 6));
    assertThat(OutlookTradingDates.previousOrSame(LocalDate.of(2026, 7, 3)))
        .isEqualTo(LocalDate.of(2026, 7, 2));
    assertThat(OutlookTradingDates.isTradingDay(LocalDate.of(2021, 12, 31))).isTrue();
  }
}
