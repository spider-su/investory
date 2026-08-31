package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LongTermAssetCommandService {
  public static final BigDecimal BOND_TAX_RATE = new BigDecimal("0.19");
  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetCashFlowService cashFlowService;
  private final LongTermAssetQueryService queries;
  private final LongTermAssetPeriodService periods;
  private final Clock applicationClock;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  public LongTermAssetCommandService(
      LongTermAssetRepository assets,
      LongTermAssetCashFlowRepository cashFlows,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetCashFlowService cashFlowService,
      LongTermAssetQueryService queries,
      LongTermAssetPeriodService periods,
      Clock applicationClock) {
    this.assets = assets;
    this.cashFlows = cashFlows;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.lifecycle = lifecycle;
    this.cashFlowService = cashFlowService;
    this.queries = queries;
    this.periods = periods;
    this.applicationClock = applicationClock;
  }

  public RentalTaxPolicyEntity saveRentalTaxPolicy(Long portfolioId, RentalTaxPolicyEntity policy) {
    return periods.saveRentalTaxPolicy(portfolioId, policy);
  }

  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    periods.deleteRentalTaxPolicy(portfolioId, policyId);
  }

  public LongTermAssetEntity updateTaxBase(Long portfolioId, Long assetId, BigDecimal taxBase) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Tax base applies only to real estate");
    if (taxBase == null || taxBase.signum() < 0)
      throw new IllegalArgumentException("Tax base must be non-negative");
    asset.setTaxBase(taxBase);
    return assets.save(asset);
  }

  public LongTermAssetEntity save(LongTermAssetEntity asset) {
    validate(asset);
    if (asset.getId() != null) {
      LongTermAssetEntity existing =
          assets
              .findByIdAndPortfolioId(asset.getId(), asset.getPortfolioId())
              .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
      LongTermAssetType originalType =
          assets
              .findTypeByIdAndPortfolioId(asset.getId(), asset.getPortfolioId())
              .orElse(existing.getType());
      if (originalType != asset.getType())
        throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
      if (asset.getType() != LongTermAssetType.BOND && bonds.existsById(asset.getId()))
        throw new IllegalArgumentException("Bond details require a bond asset");
      if (asset.getType() != LongTermAssetType.DEPOSIT && deposits.existsById(asset.getId()))
        throw new IllegalArgumentException("Deposit details require a deposit asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE
          && !cashFlows.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Cash flows require a real-estate asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE
          && asset.getType() != LongTermAssetType.CASH_RESERVE
          && !valuations.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Valuation periods require a supported asset type");
      if (asset.getType() != LongTermAssetType.BOND
          && !bondRates.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Bond-rate periods require a bond asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE && asset.isRentalTaxPaidByTenant())
        throw new IllegalArgumentException("Rental-tax settings require a real-estate asset");
    }
    LongTermAssetEntity saved = assets.save(asset);
    lifecycle.ensureInitialPeriod(saved);
    return saved;
  }

  public LongTermAssetEntity saveCashReserve(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnRate,
      String notes,
      LocalDate effectiveFrom) {
    boolean creating = id == null;
    LongTermAssetEntity asset = creating ? new LongTermAssetEntity() : owned(portfolioId, id);
    if (id != null && asset.getType() != LongTermAssetType.CASH_RESERVE)
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    if (!creating && asset.getCurrency() != currency)
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    asset.setPortfolioId(portfolioId);
    asset.setName(name);
    asset.setType(LongTermAssetType.CASH_RESERVE);
    asset.setCurrency(currency);
    asset.setCurrentValue(value);
    asset.setNotes(notes);
    if (creating) {
      asset.setAcquisitionValue(value);
      asset.setAcquisitionDate(effectiveFrom);
      asset.setActive(true);
    }
    save(asset);
    periods.replaceCurrentValuationGrowth(asset.getId(), effectiveFrom, annualReturnRate);
    return asset;
  }

  public LongTermAssetEntity saveRealEstateEntry(
      Long portfolioId, Long id, RealEstateEntryModel entry) {
    if (entry == null || entry.effectiveFrom() == null)
      throw new IllegalArgumentException("Effective-from date is required");
    LongTermAssetEntity asset = id == null ? new LongTermAssetEntity() : owned(portfolioId, id);
    if (id != null && asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    asset.setPortfolioId(portfolioId);
    asset.setName(entry.name());
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(entry.currency());
    asset.setAcquisitionDate(entry.acquisitionDate());
    asset.setAcquisitionValue(entry.acquisitionValue());
    asset.setCurrentValue(entry.currentValue());
    asset.setTaxBase(entry.taxBase());
    asset.setRentalTaxPaidByTenant(entry.rentalTaxPaidByTenant());
    asset.setNotes(entry.notes());
    asset.setActive(true);
    save(asset);
    LocalDate from = entry.effectiveFrom();
    if (entry.expectedAnnualGrowthRate() != null)
      periods.replaceValuationGrowth(asset.getId(), from, entry.expectedAnnualGrowthRate());
    return asset;
  }

  public LongTermAssetBondDetailsEntity saveBondDetails(
      Long portfolioId, Long assetId, LongTermAssetBondDetailsEntity details) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("AssetEntity is not a bond");
    if (details.getMaturityDate() == null
        || (asset.getAcquisitionDate() != null
            && details.getMaturityDate().isBefore(asset.getAcquisitionDate())))
      throw new IllegalArgumentException("Bond maturity must be on or after acquisition");
    details.setAssetId(assetId);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    validateTaxRate(details.getTaxRate(), "Bond tax rate");
    return bonds.save(details);
  }

  public void saveSimpleBond(
      Long portfolioId,
      Long assetId,
      BigDecimal rate,
      LocalDate maturityDate,
      InterestTreatment treatment) {
    if (rate == null || treatment == null)
      throw new IllegalArgumentException("Bond terms are required");
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("AssetEntity is not a bond");
    LongTermAssetBondDetailsEntity details =
        bonds.findById(assetId).orElseGet(LongTermAssetBondDetailsEntity::new);
    details.setMaturityDate(maturityDate);
    details.setInterestTreatment(treatment);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    if (details.getRedemptionValue() == null) {
      details.setRedemptionValue(asset.getAcquisitionValue());
    }
    saveBondDetails(portfolioId, assetId, details);
    LocalDate from = asset.getAcquisitionDate() == null ? today() : asset.getAcquisitionDate();
    periods.replaceBondRate(assetId, from, maturityDate, rate);
  }

  public LongTermAssetDepositDetailsEntity saveDepositDetails(
      Long portfolioId, Long assetId, LongTermAssetDepositDetailsEntity details) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.DEPOSIT)
      throw new IllegalArgumentException("AssetEntity is not a deposit");
    if (details.getMaturityDate() == null)
      throw new IllegalArgumentException("Deposit maturity is required");
    if (details.getMaturityDate() != null
        && asset.getAcquisitionDate() != null
        && details.getMaturityDate().isBefore(asset.getAcquisitionDate()))
      throw new IllegalArgumentException("Deposit maturity must be on or after acquisition");
    details.setAssetId(assetId);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    validateTaxRate(details.getTaxRate(), "Deposit tax rate");
    if (details.getAnnualInterestRate() == null || details.getAnnualInterestRate().signum() < 0)
      throw new IllegalArgumentException("Deposit interest rate must be non-negative");
    return deposits.save(details);
  }

  public void archive(Long portfolioId, Long id) {
    lifecycle.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    lifecycle.reactivate(portfolioId, id);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow) {
    return cashFlowService.add(portfolioId, assetId, flow);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow, LocalDate date) {
    LongTermAssetQueryService.RentalPeriod period =
        queries.rentalPeriod(portfolioId, assetId, date);
    if (period.effectiveFrom() != null) {
      flow.setValidFrom(period.effectiveFrom());
      flow.setValidTo(period.endDate());
    } else if (flow.getValidFrom() == null) {
      flow.setValidFrom(effectiveDate(date));
    }
    return addCashFlow(portfolioId, assetId, flow);
  }

  public void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date) {
    cashFlowService.saveRentalPeriod(
        portfolioId,
        assetId,
        effectiveFrom,
        endDate,
        queries.currentCashFlows(portfolioId, assetId, date));
  }

  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom,
      LocalDate validTo) {
    return cashFlowService.change(
        portfolioId, assetId, flowId, amount, frequency, effectiveFrom, validTo);
  }

  public LongTermAssetCashFlowEntity changeCurrentCashFlow(
      Long portfolioId, Long assetId, Long flowId, BigDecimal amount, Frequency frequency) {
    owned(portfolioId, assetId);
    LongTermAssetCashFlowEntity old = cashFlows.findById(flowId).orElseThrow();
    if (!Objects.equals(old.getAssetId(), assetId))
      throw new NoSuchElementException("Cash flow not found");
    return changeCashFlow(
        portfolioId, assetId, flowId, amount, frequency, old.getValidFrom(), old.getValidTo());
  }

  /** Compatibility overload for callers that keep the existing period open. */
  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom) {
    return changeCashFlow(portfolioId, assetId, flowId, amount, frequency, effectiveFrom, null);
  }

  public void saveExpectedPropertyGrowth(
      Long portfolioId, Long assetId, BigDecimal growthRate, LocalDate effectiveFrom) {
    periods.saveExpectedPropertyGrowth(portfolioId, assetId, growthRate, effectiveFrom);
  }

  public void deleteCashFlow(Long portfolioId, Long assetId, Long flowId) {
    cashFlowService.delete(portfolioId, assetId, flowId);
  }

  public void setCashFlowPaidByTenant(
      Long portfolioId, Long assetId, Long flowId, boolean paidByTenant) {
    cashFlowService.setPaidByTenant(portfolioId, assetId, flowId, paidByTenant);
  }

  public LongTermAssetValuationPeriodEntity addValuationPeriod(
      Long portfolioId, Long assetId, LongTermAssetValuationPeriodEntity period) {
    return periods.addValuationPeriod(portfolioId, assetId, period);
  }

  public LongTermAssetValuationPeriodEntity updateValuationPeriod(
      Long portfolioId, Long assetId, Long periodId, LongTermAssetValuationPeriodEntity period) {
    return periods.updateValuationPeriod(portfolioId, assetId, periodId, period);
  }

  public void deleteValuationPeriod(Long portfolioId, Long assetId, Long periodId) {
    periods.deleteValuationPeriod(portfolioId, assetId, periodId);
  }

  private LocalDate today() {
    return LocalDate.now(applicationClock);
  }

  private LocalDate effectiveDate(LocalDate date) {
    return date != null && date.isBefore(historyStart) ? historyStart : date;
  }

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private static void validate(LongTermAssetEntity a) {
    if (a.getName() == null || a.getName().isBlank())
      throw new IllegalArgumentException("Name is required");
    if (a.getCurrentValue() == null || a.getCurrentValue().signum() < 0)
      throw new IllegalArgumentException("Current value must be non-negative");
  }

  private static void validateTaxRate(BigDecimal rate, String label) {
    if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException(label + " must be between 0 and 1");
  }
}
