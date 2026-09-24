package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.web.LongTermAssetsRestController;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InProcessLongTermAssetsClient implements LongTermAssetsClient {
  private final LongTermAssetsRestController rest;

  public InProcessLongTermAssetsClient(LongTermAssetsRestController rest) {
    this.rest = rest;
  }

  @Override
  public LongTermOverviewView overview(Long portfolioId, LocalDate date) {
    return rest.overview(portfolioId, date);
  }

  @Override
  public List<AssetSummaryView> archived(Long portfolioId, LocalDate date) {
    return rest.archived(portfolioId, date);
  }

  @Override
  public BondView createBond(BondCommand command) {
    return rest.createBond(command.portfolioId(), command);
  }

  @Override
  public BondView updateBond(BondCommand command) {
    return rest.updateBond(command.portfolioId(), command.id(), command);
  }

  @Override
  public BondView bond(Long portfolioId, Long id) {
    return rest.bond(portfolioId, id);
  }

  @Override
  public RealEstateView createRealEstate(RealEstateCommand command) {
    return rest.createRealEstate(command.portfolioId(), command);
  }

  @Override
  public RealEstateView updateRealEstate(RealEstateCommand command) {
    return rest.updateRealEstate(command.portfolioId(), command.id(), command);
  }

  @Override
  public RealEstateView realEstate(Long portfolioId, Long id) {
    return rest.realEstate(portfolioId, id);
  }

  @Override
  public AssetSummaryView realEstateSummary(Long portfolioId, Long id, LocalDate date) {
    return rest.realEstateSummary(portfolioId, id, date);
  }

  @Override
  public CashReserveView createCashReserve(CashReserveCommand command) {
    return rest.createCashReserve(command.portfolioId(), command);
  }

  @Override
  public CashReserveView updateCashReserve(CashReserveCommand command) {
    return rest.updateCashReserve(command.portfolioId(), command.id(), command);
  }

  @Override
  public CashReserveView cashReserve(Long portfolioId, Long id) {
    return rest.cashReserve(portfolioId, id);
  }

  @Override
  public PersonalAssetView createPersonalAsset(PersonalAssetCommand command) {
    return rest.createPersonalAsset(command.portfolioId(), command);
  }

  @Override
  public PersonalAssetView updatePersonalAsset(PersonalAssetCommand command) {
    return rest.updatePersonalAsset(command.portfolioId(), command.id(), command);
  }

  @Override
  public PersonalAssetView personalAsset(Long portfolioId, Long id) {
    return rest.personalAsset(portfolioId, id);
  }

  @Override
  public RentalContractView createRentalContract(RentalContractCommand command) {
    return rest.createRentalContract(command.portfolioId(), command.assetId(), command);
  }

  @Override
  public List<RentalContractView> rentalContracts(Long portfolioId, Long assetId, LocalDate date) {
    return rest.rentalContracts(portfolioId, assetId, date);
  }

  @Override
  public RentalContractView updateRentalContract(UpdateRentalContractCommand command) {
    return rest.updateRentalContract(
        command.portfolioId(), command.assetId(), command.contractId(), command);
  }

  @Override
  public void deleteRentalContract(Long portfolioId, Long assetId, Long contractId) {
    rest.deleteRentalContract(portfolioId, assetId, contractId);
  }

  @Override
  public RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return rest.endRentalContract(portfolioId, assetId, contractId, endDate);
  }

  @Override
  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate) {
    rest.terminateRentalContract(portfolioId, assetId, contractId, terminationDate);
  }

  @Override
  public void archive(Long portfolioId, Long assetId) {
    rest.archive(portfolioId, assetId);
  }

  @Override
  public void reactivate(Long portfolioId, Long assetId) {
    rest.reactivate(portfolioId, assetId);
  }
}
