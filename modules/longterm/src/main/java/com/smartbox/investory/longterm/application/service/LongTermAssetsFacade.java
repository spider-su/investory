package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Internal long-term asset orchestration used by the public application service. */
public class LongTermAssetsFacade {
  private final LongTermAssetQueryService queries;
  private final LongTermAssetAnnualSnapshotService snapshots;
  private final LongTermAssetCommandService commands;
  private final RentalContractService rentalContracts;

  public LongTermAssetsFacade(
      LongTermAssetQueryService queries,
      LongTermAssetAnnualSnapshotService snapshots,
      LongTermAssetCommandService commands,
      RentalContractService rentalContracts) {
    this.queries = queries;
    this.snapshots = snapshots;
    this.commands = commands;
    this.rentalContracts = rentalContracts;
  }

  protected LongTermAssetsFacade(LongTermAssetsFacade source) {
    this(source.queries, source.snapshots, source.commands, source.rentalContracts);
  }

  public List<LongTermAssetSummary> listSummaries(Long portfolioId, LocalDate date) {
    return queries.list(portfolioId, date);
  }

  public List<LongTermAssetSummary> archivedSummaries(Long portfolioId, LocalDate date) {
    return queries.archived(portfolioId, date);
  }

  public List<LongTermAssetQueryService.AssetGroupSummary> groupSummaries(
      Long portfolioId, LocalDate date) {
    return queries.grouped(portfolioId, date);
  }

  public LongTermAssetQueryService.AggregateSummary aggregateSummary(
      Long portfolioId, LocalDate date) {
    return queries.aggregateForLongTermAssets(portfolioId, date);
  }

  public com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel
      historicalAnnualSnapshot(Long portfolioId, int year) {
    return snapshots.historicalAnnualSnapshot(portfolioId, year);
  }

  public AssetView asset(Long portfolioId, Long id) {
    return queries
        .get(portfolioId, id)
        .map(LongTermAssetsFacade::assetView)
        .orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
  }

  public LongTermAssetRentalContractEntity rentalContractEntity(
      Long portfolioId, Long assetId, Long contractId) {
    return queries.rentalContract(portfolioId, assetId, contractId);
  }

  public ValuationView valuationData(Long portfolioId, Long assetId, Long periodId) {
    return valuationView(queries.valuation(portfolioId, assetId, periodId));
  }

  public RentalTaxView rentalTaxPolicyData(Long portfolioId, Long policyId) {
    return rentalTaxView(queries.rentalTaxPolicy(portfolioId, policyId));
  }

  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    var detail = queries.detail(portfolioId, id, date);
    var contracts =
        detail.contracts().stream()
            .map(contract -> contractView(contract, date))
            .sorted(
                java.util.Comparator.comparing(
                        (RentalContractView contract) ->
                            contract.status() == RentalContractStatusModel.CURRENT ? 0 : 1)
                    .thenComparing(
                        RentalContractView::startDate, java.util.Comparator.reverseOrder())
                    .thenComparing(RentalContractView::id, java.util.Comparator.reverseOrder()))
            .toList();
    return new DetailView(
        assetView(detail.asset()),
        summaryView(detail.summary()),
        detail.bondDetails() == null ? null : bondDetailsView(detail.bondDetails()),
        detail.depositDetails() == null ? null : depositDetailsView(detail.depositDetails()),
        detail.valuationPeriods().stream().map(LongTermAssetsFacade::valuationView).toList(),
        detail.expectedPropertyGrowth(),
        contracts);
  }

  public AssetView create(AssetCommand command) {
    if (command.type() != LongTermAssetType.OTHER)
      throw new IllegalArgumentException("Specialized asset types require a subtype workflow");
    LongTermAssetEntity asset = toEntity(command);
    return assetView(commands.save(asset));
  }

  public AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom) {
    if (command.id() != null) {
      LongTermAssetEntity existing =
          queries
              .get(command.portfolioId(), command.id())
              .orElseThrow(() -> new AssetNotFoundException(command.portfolioId(), command.id()));
      if (existing.getType() != LongTermAssetType.CASH_RESERVE) {
        throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
      }
    }
    return assetView(
        commands.saveCashReserve(
            command.portfolioId(),
            command.id(),
            command.name(),
            command.currency(),
            command.value(),
            command.annualReturnRate(),
            command.notes(),
            effectiveFrom));
  }

  public AssetView createBond(BondCommand command) {
    AssetCommand asset =
        new AssetCommand(
            command.portfolioId(),
            null,
            command.name(),
            LongTermAssetType.BOND,
            command.currency(),
            command.acquisitionDate(),
            command.value(),
            command.value(),
            null,
            true,
            command.notes(),
            false);
    LongTermAssetEntity entity = toEntity(asset);
    AssetView saved = assetView(commands.save(entity));
    commands.saveSimpleBond(
        command.portfolioId(),
        saved.id(),
        command.annualRate(),
        command.maturityDate(),
        command.interestTreatment());
    return saved;
  }

  public LongTermAssetQueryService.PageData pageData(Long portfolioId, LocalDate date) {
    return queries.page(portfolioId, date);
  }

  public List<RentalTaxView> rentalTaxPolicies(Long portfolioId) {
    return queries.rentalTaxPolicies(portfolioId).stream()
        .map(LongTermAssetsFacade::rentalTaxView)
        .toList();
  }

  public AssetView createDeposit(DepositCommand command) {
    if (command.maturityDate() == null)
      throw new IllegalArgumentException("Deposit maturity is required");
    LongTermAssetEntity entity = new LongTermAssetEntity();
    entity.setPortfolioId(command.portfolioId());
    entity.setName(command.name());
    entity.setType(LongTermAssetType.DEPOSIT);
    entity.setCurrency(command.currency());
    entity.setAcquisitionDate(command.acquisitionDate());
    entity.setAcquisitionValue(command.value());
    entity.setCurrentValue(command.value());
    entity.setActive(true);
    entity.setNotes(command.notes());
    AssetView saved = assetView(commands.save(entity));
    saveDepositDetails(
        command.portfolioId(),
        saved.id(),
        new DepositDetailsCommand(
            command.maturityDate(),
            command.annualInterestRate(),
            command.taxRate(),
            command.interestTreatment()));
    return saved;
  }

  public AssetView update(AssetCommand command) {
    LongTermAssetEntity current =
        queries
            .get(command.portfolioId(), command.id())
            .orElseThrow(() -> new AssetNotFoundException(command.portfolioId(), command.id()));
    if (current.getType() != command.type())
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    if (current.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    current.setName(command.name());
    current.setCurrency(command.currency());
    current.setAcquisitionDate(command.acquisitionDate());
    if (command.acquisitionValue() != null) current.setAcquisitionValue(command.acquisitionValue());
    if (command.currentValue() != null) current.setCurrentValue(command.currentValue());
    if (command.taxBase() != null) current.setTaxBase(command.taxBase());
    current.setActive(command.active());
    current.setNotes(command.notes());
    commands.save(current);
    return assetView(current);
  }

  public AssetView patch(AssetPatchCommand command) {
    LongTermAssetEntity current =
        queries
            .get(command.portfolioId(), command.id())
            .orElseThrow(() -> new AssetNotFoundException(command.portfolioId(), command.id()));
    if (command.type() != null && current.getType() != command.type())
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    if (command.currency() != null && current.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    if (command.name() != null) current.setName(command.name());
    if (command.acquisitionDate() != null) current.setAcquisitionDate(command.acquisitionDate());
    if (command.acquisitionValue() != null) current.setAcquisitionValue(command.acquisitionValue());
    if (command.currentValue() != null) current.setCurrentValue(command.currentValue());
    if (command.taxBase() != null) current.setTaxBase(command.taxBase());
    if (command.active() != null) current.setActive(command.active());
    if (command.notes() != null) current.setNotes(command.notes());
    if (command.rentalTaxPaidByTenant() != null)
      current.setRentalTaxPaidByTenant(command.rentalTaxPaidByTenant());
    commands.save(current);
    return assetView(current);
  }

  public void updateBond(BondCommand command) {
    LongTermAssetEntity current =
        queries
            .get(command.portfolioId(), command.id())
            .orElseThrow(() -> new AssetNotFoundException(command.portfolioId(), command.id()));
    if (current.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    if (current.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("AssetEntity is not a bond");
    current.setName(command.name());
    current.setCurrency(command.currency());
    // Bond command.value is the current market/principal value. Historical acquisition value is
    // immutable after acquisition and is deliberately not overwritten by a normal update.
    current.setCurrentValue(command.value());
    current.setAcquisitionDate(command.acquisitionDate());
    current.setNotes(command.notes());
    commands.save(current);
    commands.saveSimpleBond(
        command.portfolioId(),
        command.id(),
        command.annualRate(),
        command.maturityDate(),
        command.interestTreatment());
  }

  public AssetView saveRealEstate(Long portfolioId, RealEstateEntryModel entry) {
    var saved = commands.saveRealEstateEntry(portfolioId, null, entry);
    rentalContracts.create(
        portfolioId,
        saved.getId(),
        entry.effectiveFrom(),
        null,
        List.of(
                term(CashFlowType.RENT, entry.monthlyRent(), Frequency.MONTHLY, false),
                term(
                    CashFlowType.PARKING_RENT,
                    entry.monthlyParkingIncome(),
                    Frequency.MONTHLY,
                    false),
                term(
                    CashFlowType.ADMIN_FEE,
                    entry.monthlyAdministrationCost(),
                    Frequency.MONTHLY,
                    true),
                term(
                    CashFlowType.OTHER_EXPENSE, entry.monthlyOtherCost(), Frequency.MONTHLY, false),
                term(CashFlowType.PROPERTY_TAX, entry.annualPropertyTax(), Frequency.ANNUAL, false),
                term(CashFlowType.INSURANCE, entry.annualInsurance(), Frequency.ANNUAL, false))
            .stream()
            .filter(input -> input.amount().signum() > 0)
            .toList());
    return assetView(saved);
  }

  public AssetView updateRealEstate(Long portfolioId, Long id, RealEstateEntryModel entry) {
    LongTermAssetEntity existing =
        queries.get(portfolioId, id).orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
    if (existing.getType() != LongTermAssetType.REAL_ESTATE) {
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    }
    LongTermAssetEntity saved = commands.saveRealEstateEntry(portfolioId, id, entry);
    commands.saveExpectedPropertyGrowth(
        portfolioId, id, entry.expectedAnnualGrowthRate(), entry.effectiveFrom());
    return assetView(saved);
  }

  public LongTermAssetRentalContractEntity createRentalContractEntity(
      RentalContractCommand command) {
    return rentalContracts.create(
        command.portfolioId(),
        command.assetId(),
        command.tenantName(),
        command.tenantEmail(),
        command.tenantPhone(),
        command.startDate(),
        command.endDate(),
        command.monthlyTaxBase(),
        command.rentalTaxPaidByTenant(),
        command.terms().stream()
            .map(
                t ->
                    new RentalContractModel.Term(
                        t.type(), t.amount(), t.frequency(), t.paidByTenant()))
            .toList(),
        command.endCurrentContractBeforeStart());
  }

  public LongTermAssetRentalContractEntity updateRentalContractEntity(
      UpdateRentalContractCommand command) {
    return rentalContracts.update(
        command.portfolioId(),
        command.assetId(),
        command.contractId(),
        command.tenantName(),
        command.tenantEmail(),
        command.tenantPhone(),
        command.startDate(),
        command.endDate(),
        command.monthlyTaxBase(),
        command.rentalTaxPaidByTenant(),
        command.usePropertyTaxPayerDefault(),
        command.terms().stream()
            .map(
                term ->
                    new RentalContractModel.Term(
                        term.type(), term.amount(), term.frequency(), term.paidByTenant()))
            .toList());
  }

  public void deleteRentalContract(Long portfolioId, Long assetId, Long contractId) {
    rentalContracts.delete(portfolioId, assetId, contractId);
  }

  public LongTermAssetRentalContractEntity endRentalContractEntity(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return rentalContracts.end(portfolioId, assetId, contractId, endDate);
  }

  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate date) {
    rentalContracts.terminate(portfolioId, assetId, contractId, date);
  }

  private static RentalContractModel.Term term(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean tenant) {
    return new RentalContractModel.Term(
        type,
        com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(amount),
        frequency,
        tenant);
  }

  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    commands.updateTaxBase(portfolioId, id, value);
  }

  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    LongTermAssetEntity asset =
        queries.get(portfolioId, id).orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Rental tax ownership applies only to real estate");
    asset.setRentalTaxPaidByTenant(paidByTenant);
    commands.save(asset);
  }

  public void archive(Long portfolioId, Long id) {
    commands.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    commands.reactivate(portfolioId, id);
  }

  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal rate, LocalDate from) {
    commands.saveExpectedPropertyGrowth(portfolioId, id, rate, from);
  }

  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setMaturityDate(command.maturityDate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    details.setRedemptionValue(command.redemptionValue());
    commands.saveBondDetails(portfolioId, id, details);
  }

  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    LongTermAssetDepositDetailsEntity details = new LongTermAssetDepositDetailsEntity();
    details.setMaturityDate(command.maturityDate());
    details.setAnnualInterestRate(command.annualInterestRate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    commands.saveDepositDetails(portfolioId, id, details);
  }

  public ValuationView addValuation(Long portfolioId, Long id, ValuationCommand command) {
    LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setExpectedAnnualGrowthRate(command.growthRate());
    return valuationView(commands.addValuationPeriod(portfolioId, id, period));
  }

  public void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command) {
    LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setExpectedAnnualGrowthRate(command.growthRate());
    commands.updateValuationPeriod(portfolioId, id, periodId, period);
  }

  public void deleteValuation(Long portfolioId, Long id, Long periodId) {
    commands.deleteValuationPeriod(portfolioId, id, periodId);
  }

  public RentalTaxView saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    return saveRentalTaxPolicy(portfolioId, null, command);
  }

  public RentalTaxView saveRentalTaxPolicy(
      Long portfolioId, Long policyId, RentalTaxCommand command) {
    RentalTaxPolicyEntity policy = new RentalTaxPolicyEntity();
    policy.setId(policyId);
    policy.setValidFrom(command.validFrom());
    policy.setValidTo(command.validTo());
    policy.setRate(command.rate());
    return rentalTaxView(commands.saveRentalTaxPolicy(portfolioId, policy));
  }

  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    commands.deleteRentalTaxPolicy(portfolioId, policyId);
  }

  private static LongTermAssetEntity toEntity(AssetCommand c) {
    LongTermAssetEntity a = new LongTermAssetEntity();
    a.setPortfolioId(c.portfolioId());
    a.setName(c.name());
    a.setType(c.type());
    a.setCurrency(c.currency());
    a.setAcquisitionDate(c.acquisitionDate());
    a.setAcquisitionValue(c.acquisitionValue());
    a.setCurrentValue(c.currentValue());
    a.setTaxBase(c.taxBase());
    a.setActive(c.active());
    a.setRentalTaxPaidByTenant(c.rentalTaxPaidByTenant());
    a.setNotes(c.notes());
    return a;
  }

  private static AssetView assetView(LongTermAssetEntity asset) {
    return new AssetView(
        asset.getId(),
        asset.getPortfolioId(),
        asset.getName(),
        asset.getType(),
        asset.getCurrency(),
        asset.getAcquisitionDate(),
        asset.getAcquisitionValue(),
        asset.getCurrentValue(),
        asset.getTaxBase(),
        asset.isActive(),
        asset.getNotes(),
        asset.isRentalTaxPaidByTenant());
  }

  private static BondDetailsView bondDetailsView(LongTermAssetBondDetailsEntity details) {
    return new BondDetailsView(
        details.getMaturityDate(),
        details.getTaxRate(),
        details.getInterestTreatment(),
        details.getRedemptionValue());
  }

  private static DepositDetailsView depositDetailsView(LongTermAssetDepositDetailsEntity details) {
    return new DepositDetailsView(
        details.getMaturityDate(),
        details.getAnnualInterestRate(),
        details.getTaxRate(),
        details.getInterestTreatment());
  }

  private static ValuationView valuationView(LongTermAssetValuationPeriodEntity period) {
    return new ValuationView(
        period.getId(),
        period.getValidFrom(),
        period.getValidTo(),
        period.getExpectedAnnualGrowthRate());
  }

  private static RentalTaxView rentalTaxView(RentalTaxPolicyEntity policy) {
    return new RentalTaxView(
        policy.getId(), policy.getValidFrom(), policy.getValidTo(), policy.getRate());
  }

  private static RentalContractView contractView(
      LongTermAssetRentalContractEntity contract, LocalDate date) {
    return new RentalContractView(
        contract.getId(),
        contract.getTenantName(),
        contract.getTenantEmail(),
        contract.getTenantPhone(),
        contract.getStartDate(),
        contract.getEndDate(),
        contract.getTerminatedDate(),
        RentalContractService.effectiveEnd(contract),
        RentalContractService.status(contract, date),
        contract.getRentalTaxPaidByTenant(),
        contract.getMonthlyTaxBase(),
        contract.getTerms().stream()
            .map(
                term ->
                    new RentalTermView(
                        term.getType(),
                        term.getAmount(),
                        term.getFrequency(),
                        term.isPaidByTenant()))
            .toList());
  }

  private static AssetSummaryView summaryView(LongTermAssetSummary summary) {
    return new AssetSummaryView(
        summary.id(),
        summary.name(),
        summary.type(),
        summary.currency(),
        summary.currentValue(),
        summary.maturityDate(),
        summary.currentAnnualRate(),
        economicsView(summary.annualEconomics()),
        summary.realEstatePlanning() == null
            ? null
            : realEstatePlanningView(summary.realEstatePlanning()),
        summary.bondPlanning() == null ? null : bondPlanningView(summary.bondPlanning()),
        summary.rentEnd());
  }

  private static AnnualEconomicsView economicsView(AnnualEconomics economics) {
    return new AnnualEconomicsView(
        economics.grossAnnualIncome(),
        economics.annualExpenses(),
        economics.annualTax(),
        economics.netAnnualIncomeBeforeTax(),
        economics.netAnnualIncomeAfterTax(),
        economics.monthlyNetIncomeAfterTax(),
        economics.grossYield(),
        economics.netYieldBeforeTax(),
        economics.netYieldAfterTax());
  }

  private static RealEstatePlanningView realEstatePlanningView(RealEstatePlanningSummary planning) {
    return new RealEstatePlanningView(
        planning.taxBase(),
        planning.totalPaymentMonthly(),
        planning.monthlyIncome(),
        planning.monthlyReduce(),
        planning.annualTax(),
        planning.netMonthlyIncome(),
        planning.incomeYield());
  }

  private static BondPlanningView bondPlanningView(BondPlanningSummary planning) {
    return new BondPlanningView(
        planning.value(),
        planning.annualRate(),
        planning.grossInterest(),
        planning.annualTax(),
        planning.netInterest(),
        planning.netYield(),
        planning.maturityDate(),
        planning.interestTreatment());
  }
}
