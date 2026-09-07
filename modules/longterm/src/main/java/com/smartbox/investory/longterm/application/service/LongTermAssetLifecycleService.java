package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.AssetNotFoundException;
import com.smartbox.investory.longterm.infrastructure.bond.BondRepository;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveRepository;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Applies unambiguous subtype changes and persists real-estate archive intervals atomically. */
@Service
@org.springframework.transaction.annotation.Transactional
class LongTermAssetLifecycleService {
  private final BondRepository bonds;
  private final RealEstateRepository realEstates;
  private final CashReserveRepository cashReserves;
  private final PersonalAssetRepository personalAssets;
  private final com.smartbox.investory.longterm.infrastructure.lifecycle
          .LongTermAssetHistoryRepository
      history;
  private final java.time.Clock clock;

  LongTermAssetLifecycleService(
      BondRepository bonds,
      RealEstateRepository realEstates,
      CashReserveRepository cashReserves,
      PersonalAssetRepository personalAssets,
      com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetHistoryRepository
          history,
      java.time.Clock clock) {
    this.bonds = bonds;
    this.realEstates = realEstates;
    this.cashReserves = cashReserves;
    this.personalAssets = personalAssets;
    this.history = history;
    this.clock = clock;
  }

  void changeArchive(Long portfolioId, Long assetId, LocalDate archivedAt) {
    var bond = bonds.findByIdAndPortfolioId(assetId, portfolioId);
    var estate = realEstates.findByIdAndPortfolioId(assetId, portfolioId);
    var cash = cashReserves.findByIdAndPortfolioId(assetId, portfolioId);
    var personal = personalAssets.findByIdAndPortfolioId(assetId, portfolioId);
    long matches =
        java.util.stream.Stream.of(bond, estate, cash, personal)
            .filter(java.util.Optional::isPresent)
            .count();
    if (matches == 0) throw new AssetNotFoundException(portfolioId, assetId);
    if (matches != 1) throw new IllegalStateException("Ambiguous Long-Term asset ID " + assetId);
    if (bond.isPresent()) {
      var row = bond.get();
      validateArchiveDate(row.getAcquisitionDate(), archivedAt);
      row.setArchivedAt(archivedAt);
      bonds.save(row);
    } else if (estate.isPresent()) {
      var row = estate.get();
      LocalDate previous = row.getArchivedAt();
      if ((previous == null) == (archivedAt == null)) return;
      LocalDate today = LocalDate.now(clock);
      if (archivedAt != null
          && row.getAcquisitionDate() != null
          && archivedAt.isBefore(row.getAcquisitionDate()))
        throw new IllegalArgumentException("Archive cannot precede acquisition");
      if (previous != null && today.isBefore(previous))
        throw new IllegalArgumentException("Reactivation cannot precede archive");
      history.transition(assetId, "REAL_ESTATE", previous, archivedAt, today);
      row.setArchivedAt(archivedAt);
      realEstates.save(row);
    } else if (cash.isPresent()) {
      var row = cash.get();
      validateArchiveDate(row.getAcquisitionDate(), archivedAt);
      row.setArchivedAt(archivedAt);
      cashReserves.save(row);
    } else {
      var row = personal.get();
      validateArchiveDate(row.getAcquisitionDate(), archivedAt);
      row.setArchivedAt(archivedAt);
      personalAssets.save(row);
    }
  }

  private static void validateArchiveDate(LocalDate acquisitionDate, LocalDate archivedAt) {
    if (archivedAt != null && acquisitionDate != null && archivedAt.isBefore(acquisitionDate))
      throw new IllegalArgumentException("Archive cannot precede acquisition");
  }
}
