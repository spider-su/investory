package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary used by adapters to manage long-term assets. */
@Service
@RequiredArgsConstructor
@Transactional
public class LongTermAssetsFacade implements LongTermAssetAnnualSnapshotReader {
  private final LongTermAssetService service;
  private final RentalContractService rentalContracts;

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    return service.list(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> archived(Long portfolioId, LocalDate date) {
    return service.archived(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetQueryService.AssetGroupSummary> grouped(
      Long portfolioId, LocalDate date) {
    return service.grouped(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.AggregateSummary aggregate(Long portfolioId, LocalDate date) {
    return service.aggregateForLongTermAssets(portfolioId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel
      historicalAnnualSnapshot(Long portfolioId, int year) {
    return service.historicalAnnualSnapshot(portfolioId, year);
  }

  @Override
  @Transactional(readOnly = true)
  public com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel
      currentAnnualSnapshot(Long portfolioId, LocalDate date) {
    return service.currentAnnualSnapshot(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public AssetView asset(Long portfolioId, Long id) {
    return service.get(portfolioId, id).map(AssetView::from).orElseThrow();
  }

  @Transactional(readOnly = true)
  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    AssetView asset = asset(portfolioId, id);
    List<ContractView> contracts =
        asset.type() == LongTermAssetType.REAL_ESTATE
            ? rentalContracts.list(portfolioId, id).stream()
                .map(contract -> ContractView.from(contract, date))
                .toList()
            : List.of();
    return new DetailView(
        asset,
        service.summary(toEntity(asset), date),
        service.bondDetails(portfolioId, id).map(BondDetailsView::from).orElse(null),
        service.depositDetails(portfolioId, id).map(DepositDetailsView::from).orElse(null),
        service.valuationPeriods(portfolioId, id).stream().map(ValuationView::from).toList(),
        service.expectedPropertyGrowth(portfolioId, id, date),
        contracts);
  }

  public AssetView create(AssetCommand command) {
    if (command.type() != LongTermAssetType.OTHER)
      throw new IllegalArgumentException("Specialized asset types require a subtype workflow");
    LongTermAssetEntity asset = toEntity(command);
    if (asset.getType() == LongTermAssetType.BOND && asset.getCurrentValue() == null)
      asset.setCurrentValue(asset.getAcquisitionValue());
    return AssetView.from(service.save(asset));
  }

  public AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom) {
    if (command.id() != null)
      service
          .get(command.portfolioId(), command.id())
          .filter(asset -> asset.getType() == LongTermAssetType.CASH_RESERVE)
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "AssetEntity type cannot be changed after creation"));
    return AssetView.from(
        service.saveCashReserve(
            command.portfolioId(),
            command.id(),
            command.name(),
            command.currency(),
            command.value(),
            percentToRate(command.annualReturnPercent()),
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
            command.notes());
    LongTermAssetEntity entity = toEntity(asset);
    AssetView saved = AssetView.from(service.save(entity));
    service.saveSimpleBond(
        command.portfolioId(),
        saved.id(),
        percentToRate(command.annualRatePercent()),
        command.maturityDate(),
        command.interestTreatment());
    return saved;
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.PageData page(Long portfolioId, LocalDate date) {
    return service.page(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<RentalTaxView> rentalTaxPolicies(Long portfolioId) {
    return service.rentalTaxPolicies(portfolioId).stream().map(RentalTaxView::from).toList();
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
    AssetView saved = AssetView.from(service.save(entity));
    saveDepositDetails(
        command.portfolioId(),
        saved.id(),
        new DepositDetailsCommand(
            command.maturityDate(),
            command.annualInterestRate(),
            command.taxRate(),
            command.interestTreatment()));
    return AssetView.from(entity);
  }

  public AssetView update(AssetCommand command) {
    LongTermAssetEntity current = service.get(command.portfolioId(), command.id()).orElseThrow();
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
    if (command.notes() != null) current.setNotes(command.notes());
    service.save(current);
    return AssetView.from(current);
  }

  public void updateBond(BondCommand command) {
    LongTermAssetEntity current = service.get(command.portfolioId(), command.id()).orElseThrow();
    if (current.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    current.setName(command.name());
    current.setCurrency(command.currency());
    // Bond command.value is the current market/principal value. Historical acquisition value is
    // immutable after acquisition and is deliberately not overwritten by a normal update.
    current.setCurrentValue(command.value());
    current.setAcquisitionDate(command.acquisitionDate());
    current.setNotes(command.notes());
    service.save(current);
    service.saveSimpleBond(
        command.portfolioId(),
        command.id(),
        percentToRate(command.annualRatePercent()),
        command.maturityDate(),
        command.interestTreatment());
  }

  public void saveRealEstate(Long portfolioId, RealEstateEntryModel entry) {
    var saved =
        service.saveRealEstateEntry(
            portfolioId, null, withGrowth(entry, percentToRate(entry.expectedAnnualGrowthRate())));
    rentalContracts.create(
        portfolioId,
        saved.getId(),
        entry.effectiveFrom(),
        null,
        List.of(
            term(CashFlowType.RENT, entry.monthlyRent(), Frequency.MONTHLY, false),
            term(CashFlowType.PARKING_RENT, entry.monthlyParkingIncome(), Frequency.MONTHLY, false),
            term(
                CashFlowType.ADMIN_FEE, entry.monthlyAdministrationCost(), Frequency.MONTHLY, true),
            term(CashFlowType.OTHER_EXPENSE, entry.monthlyOtherCost(), Frequency.MONTHLY, false),
            term(CashFlowType.PROPERTY_TAX, entry.annualPropertyTax(), Frequency.ANNUAL, false),
            term(CashFlowType.INSURANCE, entry.annualInsurance(), Frequency.ANNUAL, false)));
  }

  public LongTermAssetRentalContractEntity createRentalContract(
      LongTermAssetsApi.RentalContractCommand command) {
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

  public LongTermAssetRentalContractEntity updateRentalContract(
      LongTermAssetsApi.UpdateRentalContractCommand command) {
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

  public LongTermAssetRentalContractEntity endRentalContract(
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
        com.smartbox.investory.longterm.api.model.CashFlowTypeModel.valueOf(type.name()),
        amount == null ? BigDecimal.ZERO : amount,
        com.smartbox.investory.longterm.api.model.FrequencyModel.valueOf(frequency.name()),
        tenant);
  }

  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    service.updateTaxBase(portfolioId, id, value);
  }

  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    LongTermAssetEntity asset = service.get(portfolioId, id).orElseThrow();
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Rental tax ownership applies only to real estate");
    asset.setRentalTaxPaidByTenant(paidByTenant);
    service.save(asset);
  }

  public void archive(Long portfolioId, Long id) {
    service.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    service.reactivate(portfolioId, id);
  }

  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from) {
    service.saveExpectedPropertyGrowth(portfolioId, id, percentToRate(percent), from);
  }

  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setMaturityDate(command.maturityDate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    details.setRedemptionValue(command.redemptionValue());
    service.saveBondDetails(portfolioId, id, details);
  }

  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    LongTermAssetDepositDetailsEntity details = new LongTermAssetDepositDetailsEntity();
    details.setMaturityDate(command.maturityDate());
    details.setAnnualInterestRate(command.annualInterestRate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    service.saveDepositDetails(portfolioId, id, details);
  }

  public void addValuation(Long portfolioId, Long id, ValuationCommand command) {
    LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setExpectedAnnualGrowthRate(percentToRate(command.growthRatePercent()));
    service.addValuationPeriod(portfolioId, id, period);
  }

  public void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command) {
    LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setExpectedAnnualGrowthRate(percentToRate(command.growthRatePercent()));
    service.updateValuationPeriod(portfolioId, id, periodId, period);
  }

  public void deleteValuation(Long portfolioId, Long id, Long periodId) {
    service.deleteValuationPeriod(portfolioId, id, periodId);
  }

  public void saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    saveRentalTaxPolicy(portfolioId, null, command);
  }

  public void saveRentalTaxPolicy(Long portfolioId, Long policyId, RentalTaxCommand command) {
    RentalTaxPolicyEntity policy = new RentalTaxPolicyEntity();
    policy.setId(policyId);
    policy.setValidFrom(command.validFrom());
    policy.setValidTo(command.validTo());
    policy.setRate(
        command.ratePercent() == null ? command.rate() : percentToRate(command.ratePercent()));
    service.saveRentalTaxPolicy(portfolioId, policy);
  }

  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    service.deleteRentalTaxPolicy(portfolioId, policyId);
  }

  private static BigDecimal percentToRate(BigDecimal percent) {
    return percent == null ? null : percent.movePointLeft(2);
  }

  private static RealEstateEntryModel withGrowth(RealEstateEntryModel e, BigDecimal rate) {
    return new RealEstateEntryModel(
        e.name(),
        e.currency(),
        e.acquisitionDate(),
        e.acquisitionValue(),
        e.currentValue(),
        e.taxBase(),
        e.monthlyRent(),
        e.monthlyParkingIncome(),
        e.monthlyAdministrationCost(),
        e.monthlyOtherCost(),
        e.annualPropertyTax(),
        e.annualInsurance(),
        e.effectiveFrom(),
        rate,
        e.notes(),
        e.rentalTaxPaidByTenant());
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

  private static LongTermAssetEntity toEntity(AssetView v) {
    LongTermAssetEntity a =
        toEntity(
            new AssetCommand(
                v.portfolioId(),
                v.id(),
                v.name(),
                v.type(),
                v.currency(),
                v.acquisitionDate(),
                v.acquisitionValue(),
                v.currentValue(),
                v.taxBase(),
                v.active(),
                v.notes(),
                v.rentalTaxPaidByTenant()));
    a.setId(v.id());
    return a;
  }

  public record AssetCommand(
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

  public record BondCommand(
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

  public record DepositCommand(
      Long portfolioId,
      String name,
      CurrencyType currency,
      BigDecimal value,
      LocalDate acquisitionDate,
      LocalDate maturityDate,
      InterestTreatment interestTreatment,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      String notes) {}

  public record CashReserveCommand(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnPercent,
      String notes) {}

  public record BondDetailsCommand(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment,
      BigDecimal redemptionValue) {}

  public record DepositDetailsCommand(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment) {}

  public record ValuationCommand(
      LocalDate validFrom, LocalDate validTo, BigDecimal growthRatePercent) {}

  public record RentalTaxCommand(
      LocalDate validFrom, LocalDate validTo, BigDecimal ratePercent, BigDecimal rate) {}

  public record AssetView(
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
      boolean rentalTaxPaidByTenant) {
    public AssetView(
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
        String notes) {
      this(
          id,
          portfolioId,
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

    static AssetView from(LongTermAssetEntity a) {
      return new AssetView(
          a.getId(),
          a.getPortfolioId(),
          a.getName(),
          a.getType(),
          a.getCurrency(),
          a.getAcquisitionDate(),
          a.getAcquisitionValue(),
          a.getCurrentValue(),
          a.getTaxBase(),
          a.isActive(),
          a.getNotes(),
          a.isRentalTaxPaidByTenant());
    }
  }

  public record BondDetailsView(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment,
      BigDecimal redemptionValue) {
    static BondDetailsView from(LongTermAssetBondDetailsEntity d) {
      return new BondDetailsView(
          d.getMaturityDate(), d.getTaxRate(), d.getInterestTreatment(), d.getRedemptionValue());
    }
  }

  public record DepositDetailsView(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment) {
    static DepositDetailsView from(LongTermAssetDepositDetailsEntity d) {
      return new DepositDetailsView(
          d.getMaturityDate(), d.getAnnualInterestRate(), d.getTaxRate(), d.getInterestTreatment());
    }
  }

  public record ValuationView(
      Long id, LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {
    public ValuationView(
        LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {
      this(null, validFrom, validTo, expectedAnnualGrowthRate);
    }

    static ValuationView from(LongTermAssetValuationPeriodEntity p) {
      return new ValuationView(
          p.getId(), p.getValidFrom(), p.getValidTo(), p.getExpectedAnnualGrowthRate());
    }
  }

  public record RentalTaxView(Long id, LocalDate validFrom, LocalDate validTo, BigDecimal rate) {
    static RentalTaxView from(RentalTaxPolicyEntity policy) {
      return new RentalTaxView(
          policy.getId(), policy.getValidFrom(), policy.getValidTo(), policy.getRate());
    }
  }

  public record DetailView(
      AssetView asset,
      LongTermAssetSummary summary,
      BondDetailsView bondDetails,
      DepositDetailsView depositDetails,
      List<ValuationView> valuationPeriods,
      BigDecimal expectedPropertyGrowth,
      List<ContractView> contracts) {}

  public record ContractView(
      Long id,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate terminatedDate,
      LocalDate effectiveEndDate,
      com.smartbox.investory.longterm.api.model.RentalContractStatusModel status,
      Boolean rentalTaxPaidByTenant,
      BigDecimal monthlyTaxBase,
      List<TermView> terms) {
    public ContractView(
        Long id,
        String tenantName,
        String tenantEmail,
        String tenantPhone,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate terminatedDate,
        LocalDate effectiveEndDate,
        com.smartbox.investory.longterm.api.model.RentalContractStatusModel status,
        Boolean rentalTaxPaidByTenant,
        List<TermView> terms) {
      this(
          id,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          terminatedDate,
          effectiveEndDate,
          status,
          rentalTaxPaidByTenant,
          null,
          terms);
    }

    static ContractView from(LongTermAssetRentalContractEntity c, LocalDate date) {
      return new ContractView(
          c.getId(),
          c.getTenantName(),
          c.getTenantEmail(),
          c.getTenantPhone(),
          c.getStartDate(),
          c.getEndDate(),
          c.getTerminatedDate(),
          RentalContractService.effectiveEnd(c),
          RentalContractService.status(c, date),
          c.getRentalTaxPaidByTenant(),
          c.getMonthlyTaxBase(),
          c.getTerms().stream()
              .map(
                  t ->
                      new TermView(
                          t.getType(), t.getAmount(), t.getFrequency(), t.isPaidByTenant()))
              .toList());
    }
  }

  public record TermView(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
