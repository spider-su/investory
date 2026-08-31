package com.smartbox.investory.longterm.api.model;

import java.util.List;
import java.util.Objects;

/** Coherent Long-Term source facts used to compose one whole-wealth profile. */
public record LongTermAssetProfileSnapshotModel(
    LongTermAssetProfileSummaryModel summary,
    List<LongTermAssetProfileAssetModel> assets,
    List<LongTermAssetProjectionModel> projectionInputs,
    LongTermAssetAnnualSnapshotModel annualSnapshot) {
  public LongTermAssetProfileSnapshotModel {
    Objects.requireNonNull(summary, "summary");
    assets = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(assets);
    projectionInputs =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(projectionInputs);
    Objects.requireNonNull(annualSnapshot, "annualSnapshot");
  }
}
