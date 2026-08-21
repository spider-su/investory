package com.smartbox.investory.longterm.api;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;

import java.time.LocalDate;

/** Public Long-Term annual facts for planning history and current-year presentation. */
public interface LongTermAssetAnnualSnapshotReader {
  LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year);

  LongTermAssetAnnualSnapshotModel currentAnnualSnapshot(Long portfolioId, LocalDate date);
}
