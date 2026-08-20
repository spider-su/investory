package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.infrastructure.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns active/inactive lifecycle history while keeping LongTermAssetService as the API coordinator.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LongTermAssetLifecycleService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetLifecyclePeriodRepository periods;
  private final Clock clock;

  public void archive(Long portfolioId, Long assetId) {
    LongTermAsset asset = owned(portfolioId, assetId);
    if (!asset.isActive()) return;
    LocalDate date = LocalDate.now(clock);
    closeOpenPeriod(asset, date.minusDays(1));
    asset.setActive(false);
    asset.setArchivedAt(date);
    assets.save(asset);
  }

  public void reactivate(Long portfolioId, Long assetId) {
    LongTermAsset asset = owned(portfolioId, assetId);
    if (asset.isActive()) return;
    LocalDate date = LocalDate.now(clock);
    LongTermAssetLifecyclePeriod period = new LongTermAssetLifecyclePeriod();
    period.setAssetId(assetId);
    period.setActiveFrom(date);
    periods.save(period);
    asset.setActive(true);
    asset.setArchivedAt(null);
    assets.save(asset);
  }

  public void ensureInitialPeriod(LongTermAsset asset) {
    if (asset.getId() == null
        || !periods.findAllByAssetIdOrderByActiveFrom(asset.getId()).isEmpty()) return;
    LongTermAssetLifecyclePeriod period = new LongTermAssetLifecyclePeriod();
    period.setAssetId(asset.getId());
    period.setActiveFrom(
        asset.getAcquisitionDate() == null ? LocalDate.now(clock) : asset.getAcquisitionDate());
    period.setActiveTo(
        asset.isActive()
            ? null
            : asset.getArchivedAt() == null
                ? period.getActiveFrom()
                : asset.getArchivedAt().minusDays(1));
    periods.save(period);
  }

  public boolean activeOn(LongTermAsset asset, LocalDate date) {
    var history = periods.findAllByAssetIdOrderByActiveFrom(asset.getId());
    if (!history.isEmpty())
      return history.stream()
          .anyMatch(
              p -> LongTermAssetPeriodRules.activeOn(p.getActiveFrom(), p.getActiveTo(), date));
    return asset.getAcquisitionDate() == null || !asset.getAcquisitionDate().isAfter(date);
  }

  private void closeOpenPeriod(LongTermAsset asset, LocalDate end) {
    var open =
        periods.findAllByAssetIdOrderByActiveFrom(asset.getId()).stream()
            .filter(p -> p.getActiveTo() == null)
            .reduce((first, second) -> second)
            .orElse(null);
    if (open == null) {
      LongTermAssetLifecyclePeriod initial = new LongTermAssetLifecyclePeriod();
      initial.setAssetId(asset.getId());
      initial.setActiveFrom(asset.getAcquisitionDate() == null ? end : asset.getAcquisitionDate());
      initial.setActiveTo(end);
      periods.save(initial);
    } else {
      open.setActiveTo(end);
      periods.save(open);
    }
  }

  private LongTermAsset owned(Long portfolioId, Long assetId) {
    return assets
        .findByIdAndPortfolioId(assetId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }
}
