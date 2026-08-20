package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HistoricalLongTermAssetYearSourceTest {
  private final LongTermAssetAnnualSnapshotReader longTermAssets =
      mock(LongTermAssetAnnualSnapshotReader.class);
  private final HistoricalLongTermAssetYearSource source =
      new HistoricalLongTermAssetYearSource(longTermAssets);

  @Test
  void derivesAnnualNetRentalIncomeFromApplicableEconomics() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot(
                null, new BigDecimal("10715"), null, null, null, null));

    var result = source.read(7L, 2025);

    assertTrue(result.rentalIncomeAvailable());
    assertEquals(0, new BigDecimal("10715").compareTo(result.rentalIncome()));
  }

  @Test
  void excludesEconomicsOutsideTheHistoricalYearAndAggregatesPropertiesOnce() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot(
                null, new BigDecimal("2400"), null, null, null, null));
    var result = source.read(7L, 2025);

    assertEquals(0, new BigDecimal("2400").compareTo(result.rentalIncome()));
  }

  @Test
  void noPropertiesMeansKnownZeroButMissingEconomicsIsUnavailable() {
    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot(
                null, BigDecimal.ZERO, null, null, null, null));
    assertEquals(BigDecimal.ZERO, source.read(7L, 2025).rentalIncome());

    when(longTermAssets.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot(
                null, null, null, null, null, null));
    assertFalse(source.read(7L, 2025).rentalIncomeAvailable());
  }
}
