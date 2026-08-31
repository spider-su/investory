package com.smartbox.investory.ui.longterm;

import static com.smartbox.investory.longterm.api.LongTermAssetsApi.*;

import com.smartbox.investory.longterm.api.LongTermAssetReadApi;
import com.smartbox.investory.longterm.api.LongTermAssetWriteApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Calls the Long-Term public application API while UI and backend share one JVM. */
@Component
public class InProcessLongTermAssetsClient implements LongTermAssetsClient {
  private final LongTermAssetReadApi reads;
  private final LongTermAssetWriteApi writes;

  @Autowired
  public InProcessLongTermAssetsClient(
      @Qualifier("longTermAssetsApplicationService") LongTermAssetsApi longTermAssetsApi) {
    this.reads = longTermAssetsApi;
    this.writes = longTermAssetsApi;
  }

  public List<AssetSummaryView> list(Long portfolioId, LocalDate date) {
    return reads.list(portfolioId, date);
  }

  public List<AssetSummaryView> archived(Long portfolioId, LocalDate date) {
    return reads.archived(portfolioId, date);
  }

  public PageSnapshot page(Long portfolioId, LocalDate date) {
    return reads.page(portfolioId, date);
  }

  public List<AssetGroupView> grouped(Long portfolioId, LocalDate date) {
    return reads.grouped(portfolioId, date);
  }

  public AggregateView aggregate(Long portfolioId, LocalDate date) {
    return reads.aggregate(portfolioId, date);
  }

  public AssetView asset(Long portfolioId, Long id) {
    return reads.asset(portfolioId, id);
  }

  public RentalContractView rentalContract(Long portfolioId, Long assetId, Long contractId) {
    return reads.rentalContract(portfolioId, assetId, contractId);
  }

  public ValuationView valuation(Long portfolioId, Long assetId, Long periodId) {
    return reads.valuation(portfolioId, assetId, periodId);
  }

  public RentalTaxView rentalTaxPolicy(Long portfolioId, Long policyId) {
    return reads.rentalTaxPolicy(portfolioId, policyId);
  }

  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    return reads.details(portfolioId, id, date);
  }

  public AssetView create(AssetCommand command) {
    return writes.create(command);
  }

  public AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom) {
    return writes.saveCashReserve(command, effectiveFrom);
  }

  public AssetView createBond(BondCommand command) {
    return writes.createBond(command);
  }

  public AssetView createDeposit(DepositCommand command) {
    return writes.createDeposit(command);
  }

  public AssetView update(AssetCommand command) {
    return writes.update(command);
  }

  public AssetView patch(AssetPatchCommand command) {
    return writes.patch(command);
  }

  public void updateBond(BondCommand command) {
    writes.updateBond(command);
  }

  public AssetView saveRealEstate(Long portfolioId, RealEstateEntryModel entry) {
    return writes.saveRealEstate(portfolioId, entry);
  }

  public AssetView updateRealEstate(Long portfolioId, Long id, RealEstateEntryModel entry) {
    return writes.updateRealEstate(portfolioId, id, entry);
  }

  public RentalContractView createRentalContract(RentalContractCommand command) {
    return writes.createRentalContract(command);
  }

  public RentalContractView updateRentalContract(UpdateRentalContractCommand command) {
    return writes.updateRentalContract(command);
  }

  public void deleteRentalContract(Long portfolioId, Long assetId, Long contractId) {
    writes.deleteRentalContract(portfolioId, assetId, contractId);
  }

  public RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return writes.endRentalContract(portfolioId, assetId, contractId, endDate);
  }

  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate) {
    writes.terminateRentalContract(portfolioId, assetId, contractId, terminationDate);
  }

  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    writes.saveTaxBase(portfolioId, id, value);
  }

  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    writes.saveRentalTaxOwnership(portfolioId, id, paidByTenant);
  }

  public void archive(Long portfolioId, Long id) {
    writes.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    writes.reactivate(portfolioId, id);
  }

  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal rate, LocalDate from) {
    writes.savePropertyGrowth(portfolioId, id, rate, from);
  }

  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    writes.saveBondDetails(portfolioId, id, command);
  }

  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    writes.saveDepositDetails(portfolioId, id, command);
  }

  public ValuationView addValuation(Long portfolioId, Long id, ValuationCommand command) {
    return writes.addValuation(portfolioId, id, command);
  }

  public void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command) {
    writes.updateValuation(portfolioId, id, periodId, command);
  }

  public void deleteValuation(Long portfolioId, Long id, Long periodId) {
    writes.deleteValuation(portfolioId, id, periodId);
  }

  public RentalTaxView saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    return writes.saveRentalTaxPolicy(portfolioId, command);
  }

  public void updateRentalTaxPolicy(Long portfolioId, Long policyId, RentalTaxCommand command) {
    writes.updateRentalTaxPolicy(portfolioId, policyId, command);
  }

  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    writes.deleteRentalTaxPolicy(portfolioId, policyId);
  }

  public List<RentalTaxView> rentalTaxPolicies(Long portfolioId) {
    return reads.rentalTaxPolicies(portfolioId);
  }
}
