package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit Long-Term application boundary and sole source of calculated asset economics. */
@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class LongTermAssetsApplicationService
    implements LongTermAssetsApi, LongTermAssetProfileReader, LongTermAssetAnnualSnapshotReader {
  private final BondCommandService bonds;
  private final RealEstateCommandService realEstates;
  private final CashReserveCommandService cashReserves;
  private final PersonalAssetCommandService personalAssets;
  private final RentalContractService rentalContracts;
  private final LongTermAssetReadService reads;
  private final LongTermAssetHistoricalSnapshotService historicalSnapshots;
  private final LongTermAssetLifecycleService lifecycle;
  private final Clock clock;

  public LongTermAssetsApplicationService(
      BondCommandService bonds,
      RealEstateCommandService realEstates,
      CashReserveCommandService cashReserves,
      PersonalAssetCommandService personalAssets,
      RentalContractService rentalContracts,
      LongTermAssetReadService reads,
      LongTermAssetHistoricalSnapshotService historicalSnapshots,
      LongTermAssetLifecycleService lifecycle,
      Clock clock) {
    this.bonds = bonds;
    this.realEstates = realEstates;
    this.cashReserves = cashReserves;
    this.personalAssets = personalAssets;
    this.rentalContracts = rentalContracts;
    this.reads = reads;
    this.historicalSnapshots = historicalSnapshots;
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public LongTermOverviewView overview(Long portfolioId, LocalDate date) {
    return reads.overview(portfolioId, date);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<AssetSummaryView> archived(Long portfolioId, LocalDate date) {
    return reads.archived(portfolioId, date);
  }

  @Override
  public BondView createBond(BondCommand command) {
    return bonds.create(command);
  }

  @Override
  public BondView updateBond(BondCommand command) {
    return bonds.update(command);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public BondView bond(Long portfolioId, Long id) {
    return bonds
        .find(portfolioId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Bond not found"));
  }

  @Override
  public RealEstateView createRealEstate(RealEstateCommand command) {
    return realEstates.create(command);
  }

  @Override
  public RealEstateView updateRealEstate(RealEstateCommand command) {
    return realEstates.update(command);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public RealEstateView realEstate(Long portfolioId, Long id) {
    return realEstates
        .find(portfolioId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Real estate not found"));
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AssetSummaryView realEstateSummary(Long portfolioId, Long id, LocalDate date) {
    return reads.realEstateSummary(portfolioId, id, date);
  }

  @Override
  public CashReserveView createCashReserve(CashReserveCommand command) {
    return cashReserves.create(command);
  }

  @Override
  public CashReserveView updateCashReserve(CashReserveCommand command) {
    return cashReserves.update(command);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public CashReserveView cashReserve(Long portfolioId, Long id) {
    return cashReserves
        .find(portfolioId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Cash reserve not found"));
  }

  @Override
  public PersonalAssetView createPersonalAsset(PersonalAssetCommand command) {
    return personalAssets.create(command);
  }

  @Override
  public PersonalAssetView updatePersonalAsset(PersonalAssetCommand command) {
    return personalAssets.update(command);
  }

  @Override
  public RentalContractView createRentalContract(RentalContractCommand command) {
    if (command == null) throw new IllegalArgumentException("Rental contract is required");
    return rental(
        rentalContracts.create(
            command.portfolioId(),
            command.assetId(),
            command.tenantName(),
            command.tenantEmail(),
            command.tenantPhone(),
            command.startDate(),
            command.endDate(),
            rentalTerms(command.terms()),
            command.endCurrentContractBeforeStart()),
        LocalDate.now(clock));
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public List<RentalContractView> rentalContracts(Long portfolioId, Long assetId, LocalDate date) {
    return rentalContracts.list(portfolioId, assetId).stream()
        .map(contract -> rental(contract, date))
        .toList();
  }

  @Override
  public RentalContractView updateRentalContract(UpdateRentalContractCommand command) {
    if (command == null) throw new IllegalArgumentException("Rental contract is required");
    return rental(
        rentalContracts.update(
            command.portfolioId(),
            command.assetId(),
            command.contractId(),
            command.tenantName(),
            command.tenantEmail(),
            command.tenantPhone(),
            command.startDate(),
            command.endDate(),
            rentalTerms(command.terms())),
        LocalDate.now(clock));
  }

  @Override
  public void deleteRentalContract(Long portfolioId, Long assetId, Long contractId) {
    rentalContracts.delete(portfolioId, assetId, contractId);
  }

  @Override
  public RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return rental(
        rentalContracts.end(portfolioId, assetId, contractId, endDate), LocalDate.now(clock));
  }

  @Override
  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate) {
    rentalContracts.terminate(portfolioId, assetId, contractId, terminationDate);
  }

  @Override
  public void archive(Long portfolioId, Long assetId) {
    lifecycle.changeArchive(portfolioId, assetId, LocalDate.now(clock));
  }

  @Override
  public void reactivate(Long portfolioId, Long assetId) {
    lifecycle.changeArchive(portfolioId, assetId, null);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public PersonalAssetView personalAsset(Long portfolioId, Long id) {
    return personalAssets
        .find(portfolioId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Personal asset not found"));
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public LongTermAssetProfileSnapshotModel snapshot(Long portfolioId, LocalDate date) {
    return reads.snapshot(portfolioId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year) {
    return historicalSnapshots.snapshot(portfolioId, year);
  }

  private RentalContractView rental(RentalContractModel contract, LocalDate date) {
    return new RentalContractView(
        contract.id(),
        contract.tenantName(),
        contract.tenantEmail(),
        contract.tenantPhone(),
        contract.startDate(),
        contract.endDate(),
        contract.terminatedDate(),
        RentalContractService.effectiveEnd(contract),
        RentalContractService.status(contract, date),
        contract.terms().stream()
            .map(
                term ->
                    new RentalTermView(
                        term.type(), term.amount(), term.frequency(), term.paidByTenant()))
            .toList());
  }

  private static List<RentalContractModel.Term> rentalTerms(List<RentalTermCommand> terms) {
    if (terms == null) return List.of();
    return terms.stream()
        .map(
            term ->
                new RentalContractModel.Term(
                    term.type(), term.amount(), term.frequency(), term.paidByTenant()))
        .toList();
  }
}
