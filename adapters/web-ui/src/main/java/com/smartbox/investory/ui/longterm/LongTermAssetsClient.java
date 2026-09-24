package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.*;
import java.time.LocalDate;
import java.util.List;

/** UI boundary for Long-Term asset reads and commands. */
public interface LongTermAssetsClient {
  LongTermOverviewView overview(Long portfolioId, LocalDate date);

  List<AssetSummaryView> archived(Long portfolioId, LocalDate date);

  BondView createBond(BondCommand command);

  BondView updateBond(BondCommand command);

  BondView bond(Long portfolioId, Long id);

  RealEstateView createRealEstate(RealEstateCommand command);

  RealEstateView updateRealEstate(RealEstateCommand command);

  RealEstateView realEstate(Long portfolioId, Long id);

  AssetSummaryView realEstateSummary(Long portfolioId, Long id, LocalDate date);

  CashReserveView createCashReserve(CashReserveCommand command);

  CashReserveView updateCashReserve(CashReserveCommand command);

  CashReserveView cashReserve(Long portfolioId, Long id);

  PersonalAssetView createPersonalAsset(PersonalAssetCommand command);

  PersonalAssetView updatePersonalAsset(PersonalAssetCommand command);

  PersonalAssetView personalAsset(Long portfolioId, Long id);

  RentalContractView createRentalContract(RentalContractCommand command);

  List<RentalContractView> rentalContracts(Long portfolioId, Long assetId, LocalDate date);

  RentalContractView updateRentalContract(UpdateRentalContractCommand command);

  void deleteRentalContract(Long portfolioId, Long assetId, Long contractId);

  RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate);

  void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate);

  void archive(Long portfolioId, Long assetId);

  void reactivate(Long portfolioId, Long assetId);
}
