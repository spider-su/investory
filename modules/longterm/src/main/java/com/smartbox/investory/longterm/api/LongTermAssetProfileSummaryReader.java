package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummarySnapshotModel;
import java.time.LocalDate;

/** Long-Term facts needed for wealth summaries, excluding projection inputs. */
public interface LongTermAssetProfileSummaryReader {
  LongTermAssetProfileSummarySnapshotModel summary(Long portfolioId, LocalDate date);
}
