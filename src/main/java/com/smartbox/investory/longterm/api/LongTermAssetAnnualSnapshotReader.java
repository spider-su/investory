package com.smartbox.investory.longterm.api;

import java.time.LocalDate;

/** Public Long-Term annual facts for planning history and current-year presentation. */
public interface LongTermAssetAnnualSnapshotReader {
  LongTermAssetAnnualSnapshot historicalAnnualSnapshot(Long portfolioId, int year);

  LongTermAssetAnnualSnapshot currentAnnualSnapshot(Long portfolioId, LocalDate date);
}
