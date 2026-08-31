package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Mutation capability for Long-Term Assets consumers. */
public interface LongTermAssetWriteApi {
  AssetView create(AssetCommand command);

  AssetView patch(AssetPatchCommand command);

  AssetView update(AssetCommand command);

  AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom);

  AssetView createBond(BondCommand command);

  AssetView createDeposit(DepositCommand command);

  void updateBond(BondCommand command);

  AssetView saveRealEstate(Long portfolioId, RealEstateEntryModel entry);

  AssetView updateRealEstate(Long portfolioId, Long id, RealEstateEntryModel entry);

  RentalContractView createRentalContract(RentalContractCommand command);

  RentalContractView updateRentalContract(UpdateRentalContractCommand command);

  void deleteRentalContract(Long portfolioId, Long assetId, Long contractId);

  RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate);

  void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate);

  void saveTaxBase(Long portfolioId, Long id, BigDecimal value);

  void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant);

  void archive(Long portfolioId, Long id);

  void reactivate(Long portfolioId, Long id);

  void savePropertyGrowth(Long portfolioId, Long id, BigDecimal rate, LocalDate from);

  void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command);

  void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command);

  ValuationView addValuation(Long portfolioId, Long id, ValuationCommand command);

  void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command);

  void deleteValuation(Long portfolioId, Long id, Long periodId);

  RentalTaxView saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command);

  void updateRentalTaxPolicy(Long portfolioId, Long policyId, RentalTaxCommand command);

  void deleteRentalTaxPolicy(Long portfolioId, Long policyId);
}
