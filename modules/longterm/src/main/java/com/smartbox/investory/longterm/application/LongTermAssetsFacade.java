package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.infrastructure.*;
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
public class LongTermAssetsFacade {
  private final LongTermAssetService service;
  private final RentalContractService rentalContracts;

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    return service.list(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetService.AssetGroupSummary> grouped(Long portfolioId, LocalDate date) {
    return service.grouped(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetService.AggregateSummary aggregate(Long portfolioId, LocalDate date) {
    return service.aggregateForLongTermAssets(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public AssetView asset(Long portfolioId, Long id) {
    return service.get(portfolioId, id).map(AssetView::from).orElseThrow();
  }

  @Transactional(readOnly = true)
  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    AssetView asset = asset(portfolioId, id);
    return new DetailView(
        asset,
        service.summary(toEntity(asset), date),
        service.cashFlows(portfolioId, id).stream().map(FlowView::from).toList(),
        service.bondDetails(portfolioId, id).map(BondDetailsView::from).orElse(null),
        service.depositDetails(portfolioId, id).map(DepositDetailsView::from).orElse(null),
        service.valuationPeriods(portfolioId, id).stream().map(ValuationView::from).toList(),
        service.bondRatePeriods(portfolioId, id).stream().map(BondRateView::from).toList(),
        service.currentCashFlows(portfolioId, id, date).stream().map(FlowView::from).toList(),
        service.rentalPeriod(portfolioId, id, date),
        service.availableCashFlowTypes(portfolioId, id, date),
        service.expectedPropertyGrowth(portfolioId, id, date),
        rentalContracts.list(portfolioId, id).stream().map(ContractView::from).toList());
  }

  public AssetView create(AssetCommand command) {
    LongTermAsset asset = toEntity(command);
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
              () -> new IllegalArgumentException("Asset type cannot be changed after creation"));
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
    AssetView saved = create(asset);
    service.saveSimpleBond(
        command.portfolioId(),
        saved.id(),
        percentToRate(command.annualRatePercent()),
        command.maturityDate(),
        command.interestTreatment());
    return saved;
  }

  public AssetView update(AssetCommand command) {
    LongTermAsset current = service.get(command.portfolioId(), command.id()).orElseThrow();
    if (current.getType() != command.type())
      throw new IllegalArgumentException("Asset type cannot be changed after creation");
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
    LongTermAsset current = service.get(command.portfolioId(), command.id()).orElseThrow();
    boolean legacy =
        current.getAcquisitionValue() != null
            && current.getCurrentValue() != null
            && current.getAcquisitionValue().compareTo(current.getCurrentValue()) != 0;
    current.setName(command.name());
    current.setCurrency(command.currency());
    if (!legacy) current.setAcquisitionValue(command.value());
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

  public void saveRealEstate(Long portfolioId, RealEstateEntry entry) {
    var saved = service.saveRealEstateEntry(
        portfolioId, null, withGrowth(entry, percentToRate(entry.expectedAnnualGrowthRate())));
    rentalContracts.create(portfolioId, saved.getId(), entry.effectiveFrom(), null, List.of(
        term(CashFlowType.RENT, entry.monthlyRent(), Frequency.MONTHLY, false),
        term(CashFlowType.PARKING_RENT, entry.monthlyParkingIncome(), Frequency.MONTHLY, false),
        term(CashFlowType.ADMIN_FEE, entry.monthlyAdministrationCost(), Frequency.MONTHLY, true),
        term(CashFlowType.OTHER_EXPENSE, entry.monthlyOtherCost(), Frequency.MONTHLY, false),
        term(CashFlowType.PROPERTY_TAX, entry.annualPropertyTax(), Frequency.ANNUAL, false),
        term(CashFlowType.INSURANCE, entry.annualInsurance(), Frequency.ANNUAL, false)));
  }

  private static RentalContract.Term term(CashFlowType type, BigDecimal amount, Frequency frequency, boolean tenant) {
    return new RentalContract.Term(type, amount == null ? BigDecimal.ZERO : amount, frequency, tenant);
  }

  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    service.updateTaxBase(portfolioId, id, value);
  }

  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    LongTermAsset asset = service.get(portfolioId, id).orElseThrow();
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

  public void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date) {
    service.saveRentalPeriod(portfolioId, assetId, effectiveFrom, endDate, date);
  }

  public void addCashFlow(CashFlowCommand command, LocalDate today) {
    LongTermAssetCashFlow flow = new LongTermAssetCashFlow();
    flow.setType(command.type());
    flow.setAmount(command.amount());
    flow.setFrequency(command.frequency());
    flow.setPaidByTenant(
        command.paidByTenant() != null
            ? command.paidByTenant()
            : command.type() == CashFlowType.ADMIN_FEE || command.type() == CashFlowType.UTILITIES);
    if (command.validFrom() == null)
      service.addCashFlow(command.portfolioId(), command.assetId(), flow, today);
    else {
      flow.setValidFrom(command.validFrom());
      flow.setValidTo(command.validTo());
      service.addCashFlow(command.portfolioId(), command.assetId(), flow);
    }
  }

  public void changeCashFlow(CashFlowCommand command) {
    if (command.validFrom() == null)
      service.changeCurrentCashFlow(
          command.portfolioId(),
          command.assetId(),
          command.flowId(),
          command.amount(),
          command.frequency());
    else
      service.changeCashFlow(
          command.portfolioId(),
          command.assetId(),
          command.flowId(),
          command.amount(),
          command.frequency(),
          command.validFrom(),
          command.validTo());
    if (command.paidByTenant() != null)
      service.setCashFlowPaidByTenant(
          command.portfolioId(), command.assetId(), command.flowId(), command.paidByTenant());
  }

  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from) {
    service.saveExpectedPropertyGrowth(portfolioId, id, percentToRate(percent), from);
  }

  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    LongTermAssetBondDetails details = new LongTermAssetBondDetails();
    details.setMaturityDate(command.maturityDate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    details.setRedemptionValue(command.redemptionValue());
    service.saveBondDetails(portfolioId, id, details);
  }

  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    LongTermAssetDepositDetails details = new LongTermAssetDepositDetails();
    details.setMaturityDate(command.maturityDate());
    details.setAnnualInterestRate(command.annualInterestRate());
    details.setTaxRate(command.taxRate());
    details.setInterestTreatment(command.interestTreatment());
    service.saveDepositDetails(portfolioId, id, details);
  }

  public void addValuation(Long portfolioId, Long id, ValuationCommand command) {
    LongTermAssetValuationPeriod period = new LongTermAssetValuationPeriod();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setExpectedAnnualGrowthRate(percentToRate(command.growthRatePercent()));
    service.addValuationPeriod(portfolioId, id, period);
  }

  public void addBondRate(Long portfolioId, Long id, BondRateCommand command) {
    LongTermAssetBondRatePeriod period = new LongTermAssetBondRatePeriod();
    period.setValidFrom(command.validFrom());
    period.setValidTo(command.validTo());
    period.setAnnualInterestRate(command.annualInterestRate());
    service.addBondRatePeriod(portfolioId, id, period);
  }

  public void saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    RentalTaxPolicy policy = new RentalTaxPolicy();
    policy.setValidFrom(command.validFrom());
    policy.setValidTo(command.validTo());
    policy.setRate(
        command.ratePercent() == null ? command.rate() : percentToRate(command.ratePercent()));
    service.saveRentalTaxPolicy(portfolioId, policy);
  }

  private static BigDecimal percentToRate(BigDecimal percent) {
    return percent == null ? null : percent.movePointLeft(2);
  }

  private static RealEstateEntry withGrowth(RealEstateEntry e, BigDecimal rate) {
    return new RealEstateEntry(
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

  private static LongTermAsset toEntity(AssetCommand c) {
    LongTermAsset a = new LongTermAsset();
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

  private static LongTermAsset toEntity(AssetView v) {
    LongTermAsset a =
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
                v.notes()));
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

  public record CashReserveCommand(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnPercent,
      String notes) {}

  public record CashFlowCommand(
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

  public record BondRateCommand(
      LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {}

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

    static AssetView from(LongTermAsset a) {
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

  public record FlowView(
      Long id,
      Long assetId,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo,
      boolean paidByTenant) {
    static FlowView from(LongTermAssetCashFlow f) {
      return new FlowView(
          f.getId(),
          f.getAssetId(),
          f.getType(),
          f.getAmount(),
          f.getFrequency(),
          f.getValidFrom(),
          f.getValidTo(),
          f.isPaidByTenant());
    }
  }

  public record BondDetailsView(
      LocalDate maturityDate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment,
      BigDecimal redemptionValue) {
    static BondDetailsView from(LongTermAssetBondDetails d) {
      return new BondDetailsView(
          d.getMaturityDate(), d.getTaxRate(), d.getInterestTreatment(), d.getRedemptionValue());
    }
  }

  public record DepositDetailsView(
      LocalDate maturityDate,
      BigDecimal annualInterestRate,
      BigDecimal taxRate,
      InterestTreatment interestTreatment) {
    static DepositDetailsView from(LongTermAssetDepositDetails d) {
      return new DepositDetailsView(
          d.getMaturityDate(), d.getAnnualInterestRate(), d.getTaxRate(), d.getInterestTreatment());
    }
  }

  public record ValuationView(
      LocalDate validFrom, LocalDate validTo, BigDecimal expectedAnnualGrowthRate) {
    static ValuationView from(LongTermAssetValuationPeriod p) {
      return new ValuationView(p.getValidFrom(), p.getValidTo(), p.getExpectedAnnualGrowthRate());
    }
  }

  public record BondRateView(
      LocalDate validFrom, LocalDate validTo, BigDecimal annualInterestRate) {
    static BondRateView from(LongTermAssetBondRatePeriod p) {
      return new BondRateView(p.getValidFrom(), p.getValidTo(), p.getAnnualInterestRate());
    }
  }

  public record DetailView(
      AssetView asset,
      LongTermAssetSummary summary,
      List<FlowView> cashFlows,
      BondDetailsView bondDetails,
      DepositDetailsView depositDetails,
      List<ValuationView> valuationPeriods,
      List<BondRateView> bondRatePeriods,
      List<FlowView> currentCashFlows,
      LongTermAssetService.RentalPeriod rentalPeriod,
      List<CashFlowType> availableCashFlowTypes,
      BigDecimal expectedPropertyGrowth,
      List<ContractView> contracts) {}

  public record ContractView(Long id, LocalDate startDate, LocalDate endDate, LocalDate terminatedDate, List<TermView> terms) {
    static ContractView from(LongTermAssetRentalContract c) {
      return new ContractView(c.getId(), c.getStartDate(), c.getEndDate(), c.getTerminatedDate(), c.getTerms().stream()
          .map(t -> new TermView(t.getType(), t.getAmount(), t.getFrequency(), t.isPaidByTenant())).toList());
    }
  }

  public record TermView(CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
