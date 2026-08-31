package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import java.time.LocalDate;

/** Public Long-Term read boundary for retirement profile composition and simulation inputs. */
public interface LongTermAssetProfileReader {
  LongTermAssetProfileSnapshotModel snapshot(Long portfolioId, LocalDate date);
}
