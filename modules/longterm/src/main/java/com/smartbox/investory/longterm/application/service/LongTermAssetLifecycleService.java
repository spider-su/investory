package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns active/inactive lifecycle history for long-term assets. */
@Service
@RequiredArgsConstructor
@Transactional
public class LongTermAssetLifecycleService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetLifecyclePeriodRepository periods;
  private final Clock clock;

  public void archive(Long portfolioId, Long assetId) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (!asset.isActive()) return;
    LocalDate date = LocalDate.now(clock);
    closeOpenPeriod(asset, date.minusDays(1));
    asset.setActive(false);
    asset.setArchivedAt(date);
    assets.save(asset);
  }

  public void reactivate(Long portfolioId, Long assetId) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.isActive()) return;
    LocalDate date = LocalDate.now(clock);
    LongTermAssetLifecyclePeriodEntity sameDay =
        periods.findAllByAssetIdOrderByActiveFrom(assetId).stream()
            .filter(period -> date.equals(period.getActiveFrom()))
            .reduce((first, second) -> second)
            .orElse(null);
    if (sameDay != null) {
      sameDay.setActiveTo(null);
      periods.save(sameDay);
    } else {
      LongTermAssetLifecyclePeriodEntity period = new LongTermAssetLifecyclePeriodEntity();
      period.setAssetId(assetId);
      period.setActiveFrom(date);
      periods.save(period);
    }
    asset.setActive(true);
    asset.setArchivedAt(null);
    assets.save(asset);
  }

  public void ensureInitialPeriod(LongTermAssetEntity asset) {
    if (asset.getId() == null
        || !periods.findAllByAssetIdOrderByActiveFrom(asset.getId()).isEmpty()) return;
    LongTermAssetLifecyclePeriodEntity period = new LongTermAssetLifecyclePeriodEntity();
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

  public boolean activeOn(LongTermAssetEntity asset, LocalDate date) {
    var history = periods.findAllByAssetIdOrderByActiveFrom(asset.getId());
    return activeOn(asset, date, history);
  }

  /** Evaluates a portfolio population after one lifecycle-history query. */
  public List<LongTermAssetEntity> activeAt(List<LongTermAssetEntity> assets, LocalDate date) {
    if (assets.isEmpty()) return List.of();
    Map<Long, List<LongTermAssetLifecyclePeriodEntity>> historyByAssetId =
        periods
            .findAllByAssetIdInOrderByAssetIdAscActiveFromAsc(
                assets.stream().map(LongTermAssetEntity::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(LongTermAssetLifecyclePeriodEntity::getAssetId));
    return assets.stream()
        .filter(
            asset -> activeOn(asset, date, historyByAssetId.getOrDefault(asset.getId(), List.of())))
        .toList();
  }

  private boolean activeOn(
      LongTermAssetEntity asset, LocalDate date, List<LongTermAssetLifecyclePeriodEntity> history) {
    if (!history.isEmpty()) {
      // A date-only period cannot distinguish archive and reactivation on the same day.
      // The persisted current flag is therefore authoritative for today's boundary.
      if (date.equals(LocalDate.now(clock))) return asset.isActive();
      return history.stream()
          .anyMatch(
              p -> LongTermAssetPeriodRules.activeOn(p.getActiveFrom(), p.getActiveTo(), date));
    }
    return asset.isActive()
        && (asset.getAcquisitionDate() == null || !asset.getAcquisitionDate().isAfter(date))
        && (asset.getArchivedAt() == null || asset.getArchivedAt().isAfter(date));
  }

  private void closeOpenPeriod(LongTermAssetEntity asset, LocalDate end) {
    var open =
        periods.findAllByAssetIdOrderByActiveFrom(asset.getId()).stream()
            .filter(p -> p.getActiveTo() == null)
            .reduce((first, second) -> second)
            .orElse(null);
    if (open == null) {
      LongTermAssetLifecyclePeriodEntity initial = new LongTermAssetLifecyclePeriodEntity();
      initial.setAssetId(asset.getId());
      initial.setActiveFrom(
          asset.getAcquisitionDate() == null || asset.getAcquisitionDate().isAfter(end)
              ? end
              : asset.getAcquisitionDate());
      initial.setActiveTo(end);
      periods.save(initial);
    } else {
      // Date-only lifecycle data cannot represent two distinct transitions inside one day.
      // Collapse same-day transitions into one valid one-day period instead of producing
      // active_to < active_from. The current active flag remains authoritative for current views.
      open.setActiveTo(end.isBefore(open.getActiveFrom()) ? open.getActiveFrom() : end);
      periods.save(open);
    }
  }

  private LongTermAssetEntity owned(Long portfolioId, Long assetId) {
    return assets
        .findByIdAndPortfolioId(assetId, portfolioId)
        .orElseThrow(() -> new AssetNotFoundException(portfolioId, assetId));
  }
}
