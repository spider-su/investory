package com.smartbox.investory.ui.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
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

  @Test
  void formatsLongTermGroupsAndAnnualAmountsForPeople() {
    assertThat(UiPresentation.assetGroupType(LongTermAssetType.BOND)).isEqualTo("Bonds");
    assertThat(UiPresentation.assetGroupType(LongTermAssetType.PERSONAL_ASSET))
        .isEqualTo("Personal assets");
    assertThat(UiPresentation.moneyWhole(new java.math.BigDecimal("177200.9")))
        .isEqualTo("177,201");
  }

  @Test
  void mapsReconciliationDiagnosticsToReadableLabelsWithoutChangingCodes() {
    assertThat(UiPresentation.reconciliationCheckpoint(ReconciliationCheckpoint.C0))
        .isEqualTo("Import consistency");
    assertThat(UiPresentation.reconciliationCheckCode("IMPORT_NOT_COMPLETED"))
        .isEqualTo("Import not completed");
    assertThat(UiPresentation.reconciliationCode("IMPORT_NOT_COMPLETED"))
        .isEqualTo("Code: IMPORT_NOT_COMPLETED");
  }
}
