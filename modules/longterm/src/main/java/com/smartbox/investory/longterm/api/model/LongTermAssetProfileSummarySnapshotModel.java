package com.smartbox.investory.longterm.api.model;

import java.util.List;

/** Summary-only Long-Term profile facts. */
public record LongTermAssetProfileSummarySnapshotModel(
    LongTermAssetProfileSummaryModel summary,
    List<LongTermAssetProfileAssetModel> assets,
    LongTermAssetAnnualSnapshotModel annualSnapshot) {
  public LongTermAssetProfileSummarySnapshotModel {
    assets = List.copyOf(assets == null ? List.of() : assets);
  }
}
