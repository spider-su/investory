package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public application contract for long-term asset management and read models. */
public interface LongTermAssetsApi {
  List<AssetSummaryView> list(Long portfolioId, LocalDate date);

  List<AssetSummaryView> archived(Long portfolioId, LocalDate date);

  PageSnapshot page(Long portfolioId, LocalDate date);

  List<AssetGroupView> grouped(Long portfolioId, LocalDate date);

  AggregateView aggregate(Long portfolioId, LocalDate date);

  AssetView asset(Long portfolioId, Long id);

  DetailView details(Long portfolioId, Long id, LocalDate date);

  AssetView create(AssetCommand command);

  AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom);

  AssetView createBond(BondCommand command);

  AssetView createDeposit(DepositCommand command);

  void update(AssetCommand command);

  void updateBond(BondCommand command);

  void saveRealEstate(Long portfolioId, RealEstateEntryModel entry);

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

  void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from);

  void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command);

  void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command);

  void addValuation(Long portfolioId, Long id, ValuationCommand command);

  void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command);

  void deleteValuation(Long portfolioId, Long id, Long periodId);

  void addBondRate(Long portfolioId, Long id, BondRateCommand command);

  void updateBondRate(Long portfolioId, Long id, Long periodId, BondRateCommand command);

  void deleteBondRate(Long portfolioId, Long id, Long periodId);

  void saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command);

  void updateRentalTaxPolicy(Long portfolioId, Long policyId, RentalTaxCommand command);

  void deleteRentalTaxPolicy(Long portfolioId, Long policyId);

  List<RentalTaxView> rentalTaxPolicies(Long portfolioId);

  record AssetCommand(
      Long portfolioId,
      Long id,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal taxBase,
      boolean active,
      String notes,
      boolean rentalTaxPaidByTenant) {
    public AssetCommand(
        Long portfolioId,
        Long id,
        String name,
        LongTermAssetTypeModel type,
        CurrencyType currency,
        LocalDate acquisitionDate,
        BigDecimal acquisitionValue,
        BigDecimal currentValue,
        BigDecimal taxBase,
        boolean active,
        String notes) {
      this(
          portfolioId,
          id,
          name,
          type,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          taxBase,
          active,
          notes,
          false);
    }
  }

  record BondCommand(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      LocalDate acquisitionDate,
      LocalDate maturityDate,
      InterestTreatmentModel interestTreatment,
      BigDecimal annualRatePercent,
      String notes) {}

  record DepositCommand(
      Long portfolioId,
      String name,
      CurrencyType currency,
      BigDecimal value,
      LocalDate acquisitionDate,
      LocalDate maturityDate,
      InterestTreatmentModel interestTreatment,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      String notes) {}

  record CashReserveCommand(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnPercent,
      String notes) {}

  record RentalContractCommand(
      Long portfolioId,
      Long assetId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal monthlyTaxBase,
      Boolean rentalTaxPaidByTenant,
      boolean endCurrentContractBeforeStart,
      List<RentalTermCommand> terms) {
    public RentalContractCommand(
        Long portfolioId,
        Long assetId,
        String tenantName,
        String tenantEmail,
        String tenantPhone,
        LocalDate startDate,
        LocalDate endDate,
        Boolean rentalTaxPaidByTenant,
        boolean endCurrentContractBeforeStart,
        List<RentalTermCommand> terms) {
      this(
          portfolioId,
          assetId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          null,
          rentalTaxPaidByTenant,
          endCurrentContractBeforeStart,
          terms);
    }

    public RentalContractCommand(
        Long portfolioId,
        Long assetId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean rentalTaxPaidByTenant,
        List<RentalTermCommand> terms) {
      this(
          portfolioId,
          assetId,
          null,
          null,
          null,
          startDate,
          endDate,
          null,
          rentalTaxPaidByTenant,
          false,
          terms);
    }
  }

  record UpdateRentalContractCommand(
      Long portfolioId,
      Long assetId,
      Long contractId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal monthlyTaxBase,
      Boolean rentalTaxPaidByTenant,
      boolean usePropertyTaxPayerDefault,
      List<RentalTermCommand> terms) {
    public UpdateRentalContractCommand(
        Long portfolioId,
        Long assetId,
        Long contractId,
        String tenantName,
        String tenantEmail,
        String tenantPhone,
        LocalDate startDate,
        LocalDate endDate,
        Boolean rentalTaxPaidByTenant,
        List<RentalTermCommand> terms) {
      this(
          portfolioId,
          assetId,
          contractId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          null,
          rentalTaxPaidByTenant,
          false,
          terms);
    }
  }

  record RentalTermCommand(
      CashFlowTypeModel type, BigDecimal amount, FrequencyModel frequency, boolean paidByTenant) {}

  record BondDetailsCommand(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatmentModel interestTreatment,
      BigDecimal redemptionValue) {}

  record DepositDetailsCommand(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatmentModel interestTreatment) {}

  record ValuationCommand(LocalDate validFrom, LocalDate validTo, BigDecimal growthRatePercent) {}

  record BondRateCommand(LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {}

  record RentalTaxCommand(
      LocalDate validFrom, LocalDate validTo, BigDecimal ratePercent, BigDecimal rate) {}

  record AssetView(
      Long id,
      Long portfolioId,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal taxBase,
      boolean active,
      String notes,
      boolean rentalTaxPaidByTenant) {}

  record BondDetailsView(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatmentModel interestTreatment,
      BigDecimal redemptionValue) {}

  record DepositDetailsView(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatmentModel interestTreatment) {}

  record ValuationView(
      Long id, LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {
    public ValuationView(
        LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {
      this(null, validFrom, validTo, expectedAnnualGrowthRate);
    }
  }

  record BondRateView(
      Long id, LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {
    public BondRateView(LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {
      this(null, validFrom, validTo, annualInterestRate);
    }
  }

  record RentalTaxView(Long id, LocalDate validFrom, LocalDate validTo, BigDecimal rate) {}

  record AnnualEconomicsView(
      BigDecimal grossAnnualIncome,
      BigDecimal annualExpenses,
      BigDecimal annualTax,
      BigDecimal netAnnualIncomeBeforeTax,
      BigDecimal netAnnualIncomeAfterTax,
      BigDecimal grossYield,
      BigDecimal netYieldBeforeTax,
      BigDecimal netYieldAfterTax) {}

  record RealEstatePlanningView(
      BigDecimal taxBase,
      BigDecimal totalPaymentMonthly,
      BigDecimal monthlyIncome,
      BigDecimal monthlyReduce,
      BigDecimal annualTax,
      BigDecimal netMonthlyIncome,
      BigDecimal incomeYield) {}

  record BondPlanningView(
      BigDecimal value,
      BigDecimal annualRate,
      BigDecimal grossInterest,
      BigDecimal annualTax,
      BigDecimal netInterest,
      BigDecimal netYield,
      LocalDate maturityDate,
      InterestTreatmentModel interestTreatment) {}

  record RealEstateGroupPlanningView(
      BigDecimal totalPaymentMonthly,
      BigDecimal netMonthlyIncome,
      BigDecimal monthlyReduce,
      BigDecimal taxBase,
      BigDecimal monthlyTax,
      BigDecimal netYield) {}

  record AssetSummaryView(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      BigDecimal currentValue,
      LocalDate maturityDate,
      BigDecimal currentAnnualRate,
      AnnualEconomicsView annualEconomics,
      RealEstatePlanningView realEstatePlanning,
      BondPlanningView bondPlanning,
      LocalDate rentEnd) {}

  record AggregateView(
      CurrencyType currency, BigDecimal totalCurrentValue, AnnualEconomicsView annualEconomics) {}

  record AssetGroupView(
      String key,
      String title,
      CurrencyType currency,
      List<AssetSummaryView> assets,
      BigDecimal totalValue,
      AnnualEconomicsView annualEconomics,
      RealEstateGroupPlanningView realEstatePlanning) {}

  record PageSnapshot(
      List<AssetSummaryView> assets, List<AssetGroupView> groups, AggregateView aggregate) {}

  record DetailView(
      AssetView asset,
      AssetSummaryView summary,
      BondDetailsView bondDetails,
      DepositDetailsView depositDetails,
      List<ValuationView> valuationPeriods,
      List<BondRateView> bondRatePeriods,
      BigDecimal expectedPropertyGrowth,
      List<RentalContractView> contracts) {}

  record RentalContractView(
      Long id,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate terminatedDate,
      LocalDate effectiveEndDate,
      RentalContractStatusModel status,
      Boolean rentalTaxPaidByTenant,
      BigDecimal monthlyTaxBase,
      List<RentalTermView> terms) {
    public RentalContractView(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate terminatedDate,
        Boolean rentalTaxPaidByTenant,
        List<RentalTermView> terms) {
      this(
          id,
          null,
          null,
          null,
          startDate,
          endDate,
          terminatedDate,
          earlier(endDate, terminatedDate),
          null,
          rentalTaxPaidByTenant,
          null,
          terms);
    }

    private static LocalDate earlier(LocalDate expectedEnd, LocalDate termination) {
      if (expectedEnd == null) return termination;
      if (termination == null) return expectedEnd;
      return expectedEnd.isBefore(termination) ? expectedEnd : termination;
    }
  }

  record RentalTermView(
      CashFlowTypeModel type, BigDecimal amount, FrequencyModel frequency, boolean paidByTenant) {}
}
