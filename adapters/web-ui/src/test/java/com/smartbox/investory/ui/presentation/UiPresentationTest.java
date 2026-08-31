package com.smartbox.investory.ui.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UI Presentation")
class UiPresentationTest {

  @DisplayName("formats Dates For People And Keeps Iso Out Of Rendered Labels")
  @Test
  void formatsDatesForPeopleAndKeepsIsoOutOfRenderedLabels() {
    assertThat(UiPresentation.date(LocalDate.of(2026, 4, 1))).isEqualTo("1 Apr 2026");
    assertThat(
            UiPresentation.dateTime(
                ZonedDateTime.of(2026, 8, 27, 0, 1, 0, 0, ZoneId.of("Europe/Warsaw"))))
        .isEqualTo("27 Aug 2026, 00:01 CEST");
  }
}
