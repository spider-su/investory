package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public application contract for long-term asset management and read models. */
public interface LongTermAssetsApi {
  List<AssetSummaryView> list(Long portfolioId, LocalDate date);

  List<AssetGroupView> grouped(Long portfolioId, LocalDate date);

  AggregateView aggregate(Long portfolioId, LocalDate date);

  AssetView asset(Long portfolioId, Long id);

  DetailView details(Long portfolioId, Long id, LocalDate date);

  AssetView create(AssetCommand command);

  AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom);

  AssetView createBond(BondCommand command);

  void update(AssetCommand command);

  void updateBond(BondCommand command);

  void saveRealEstate(Long portfolioId, RealEstateEntryModel entry);

  RentalContractView createRentalContract(RentalContractCommand command);

  RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate);

  void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate);

  void saveTaxBase(Long portfolioId, Long id, BigDecimal value);

  void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant);

  void archive(Long portfolioId, Long id);

  void reactivate(Long portfolioId, Long id);

  void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date);

  void addCashFlow(CashFlowCommand command, LocalDate today);

  void changeCashFlow(CashFlowCommand command);

  void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from);

  void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command);

  void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command);

  void addValuation(Long portfolioId, Long id, ValuationCommand command);

  void addBondRate(Long portfolioId, Long id, BondRateCommand command);

  void saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command);

  record AssetCommand(
      Long portfolioId,
      Long id,
      String name,
      LongTermAssetType type,
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
        LongTermAssetType type,
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
      InterestTreatment interestTreatment,
      BigDecimal annualRatePercent,
      String notes) {}

  record CashReserveCommand(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnPercent,
      String notes) {}

  record CashFlowCommand(
      Long portfolioId,
      Long assetId,
      Long flowId,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo,
      Boolean paidByTenant) {
    public CashFlowCommand(
        Long portfolioId,
        Long assetId,
        Long flowId,
        CashFlowType type,
        BigDecimal amount,
        Frequency frequency,
        LocalDate validFrom,
        LocalDate validTo) {
      this(portfolioId, assetId, flowId, type, amount, frequency, validFrom, validTo, null);
    }
  }

  record RentalContractCommand(
      Long portfolioId,
      Long assetId,
      LocalDate startDate,
      LocalDate endDate,
      Boolean rentalTaxPaidByTenant,
      List<RentalTermCommand> terms) {}

  record RentalTermCommand(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}

  record BondDetailsCommand(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment,
      BigDecimal redemptionValue) {}

  record DepositDetailsCommand(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment) {}

  record ValuationCommand(LocalDate validFrom, LocalDate validTo, BigDecimal growthRatePercent) {}

  record BondRateCommand(LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {}

  record RentalTaxCommand(
      LocalDate validFrom, LocalDate validTo, BigDecimal ratePercent, BigDecimal rate) {}

  record AssetView(
      Long id,
      Long portfolioId,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      BigDecimal taxBase,
      boolean active,
      String notes,
      boolean rentalTaxPaidByTenant) {}

  record FlowView(
      Long id,
      Long assetId,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo,
      boolean paidByTenant) {}

  record BondDetailsView(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment,
      BigDecimal redemptionValue) {}

  record DepositDetailsView(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment) {}

  record ValuationView(
      LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {}

  record BondRateView(LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {}

  record RentalPeriodView(LocalDate effectiveFrom, LocalDate endDate) {}

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
      BigDecimal totalPaymentMonthly,
      BigDecimal monthlyIncome,
      BigDecimal monthlyReduce,
      BigDecimal annualTax,
      BigDecimal netMonthlyIncome) {}

  record BondPlanningView(
      BigDecimal value,
      BigDecimal annualRate,
      BigDecimal grossInterest,
      BigDecimal annualTax,
      BigDecimal netInterest,
      BigDecimal netYield,
      LocalDate maturityDate,
      InterestTreatment interestTreatment) {}

  record RealEstateGroupPlanningView(
      BigDecimal totalPaymentMonthly,
      BigDecimal netMonthlyIncome,
      BigDecimal monthlyTax,
      BigDecimal netYield) {}

  record AssetSummaryView(
      Long id,
      String name,
      LongTermAssetType type,
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

  record DetailView(
      AssetView asset,
      AssetSummaryView summary,
      List<FlowView> cashFlows,
      BondDetailsView bondDetails,
      DepositDetailsView depositDetails,
      List<ValuationView> valuationPeriods,
      List<BondRateView> bondRatePeriods,
      List<FlowView> currentCashFlows,
      RentalPeriodView rentalPeriod,
      List<CashFlowType> availableCashFlowTypes,
      BigDecimal expectedPropertyGrowth,
      List<RentalContractView> contracts) {}

  record RentalContractView(
      Long id,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate terminatedDate,
      Boolean rentalTaxPaidByTenant,
      List<RentalTermView> terms) {}

  record RentalTermView(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
