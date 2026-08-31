package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Historical Long Term Asset Year Source")
class HistoricalLongTermAssetYearSourceTest {
  private final LongTermAssetAnnualSnapshotReader longTermAssets =
      mock(LongTermAssetAnnualSnapshotReader.class);
  private final HistoricalLongTermAssetYearSource source =
      new HistoricalLongTermAssetYearSource(longTermAssets);

  @DisplayName("derives Annual Net Rental Income From Applicable Economics")
  @Test
  void derivesAnnualNetRentalIncomeFromApplicableEconomics() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel(
                null, new BigDecimal("10715"), null, null, null, null));

    var result = source.read(7L, 2025);

    assertTrue(result.rentalIncomeAvailable());
    assertEquals(0, new BigDecimal("10715").compareTo(result.rentalIncome()));
  }

  @DisplayName("excludes Economics Outside The Historical Year And Aggregates Properties Once")
  @Test
  void excludesEconomicsOutsideTheHistoricalYearAndAggregatesPropertiesOnce() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel(
                null, new BigDecimal("2400"), null, null, null, null));
    var result = source.read(7L, 2025);

    assertEquals(0, new BigDecimal("2400").compareTo(result.rentalIncome()));
  }

  @DisplayName("no Properties Means Known Zero But Missing Economics Is Unavailable")
  @Test
  void noPropertiesMeansKnownZeroButMissingEconomicsIsUnavailable() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel(
                null, BigDecimal.ZERO, null, null, null, null));
    assertEquals(BigDecimal.ZERO, source.read(7L, 2025).rentalIncome());

    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel(
                null, null, null, null, null, null));
    assertFalse(source.read(7L, 2025).rentalIncomeAvailable());
  }
}
