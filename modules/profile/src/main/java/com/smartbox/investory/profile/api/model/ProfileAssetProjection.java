package com.smartbox.investory.profile.api.model;

import com.smartbox.investory.shared.projection.ProjectionSource;
import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable asset facts captured for deterministic future planning.
 *
 * <p>Profile does not own rental-growth assumptions, so its baseline value is zero. Retirement
 * overlays the scenario-owned rental growth when it builds effective simulation assumptions.
 */
public record ProfileAssetProjection(
    List<ProjectedLongTermAsset> assets,
    BigDecimal rentalIncomeGrowthRate,
    int rentalIncomeBaseYear,
    ProjectionSource source) {
  public static final ProfileAssetProjection EMPTY =
      new ProfileAssetProjection(List.of(), BigDecimal.ZERO, 0, ProjectionSource.PROJECTED);

  public ProfileAssetProjection {
    assets = CollectionUtils.immutableListOrEmpty(assets);
    rentalIncomeGrowthRate =
        rentalIncomeGrowthRate == null ? BigDecimal.ZERO : rentalIncomeGrowthRate;
    source = source == null ? ProjectionSource.PROJECTED : source;
  }
}
