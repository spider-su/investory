package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;

/** Public Long-Term annual facts for historical planning. */
public interface LongTermAssetAnnualSnapshotReader {
  LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year);
}
