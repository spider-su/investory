package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
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
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetQueryService queries;
  private final LongTermAssetPeriodService periods;
  private final Clock applicationClock;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  public LongTermAssetCommandService(
      LongTermAssetRepository assets,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetQueryService queries,
      LongTermAssetPeriodService periods,
      Clock applicationClock) {
    this.assets = assets;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.lifecycle = lifecycle;
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
              .orElseThrow(() -> new AssetNotFoundException(asset.getPortfolioId(), asset.getId()));
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
    if (annualReturnRate != null) {
      LongTermAssetRateRules.requireReturnRate(annualReturnRate, "Cash reserve return rate");
    }
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
    LongTermAssetRateRules.requireReturnRate(
        details.getAnnualInterestRate(), "Deposit interest rate");
    return deposits.save(details);
  }

  public void archive(Long portfolioId, Long id) {
    lifecycle.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    lifecycle.reactivate(portfolioId, id);
  }

  public void saveExpectedPropertyGrowth(
      Long portfolioId, Long assetId, BigDecimal growthRate, LocalDate effectiveFrom) {
    periods.saveExpectedPropertyGrowth(portfolioId, assetId, growthRate, effectiveFrom);
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
        .orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
  }

  private static void validate(LongTermAssetEntity a) {
    if (a.getPortfolioId() == null) throw new IllegalArgumentException("Portfolio is required");
    if (a.getName() == null || a.getName().isBlank())
      throw new IllegalArgumentException("Name is required");
    if (a.getType() == null) throw new IllegalArgumentException("Asset type is required");
    if (a.getCurrency() == null) throw new IllegalArgumentException("Asset currency is required");
    if (a.getCurrentValue() == null || a.getCurrentValue().signum() < 0)
      throw new IllegalArgumentException("Current value must be non-negative");
    if (a.getAcquisitionValue() != null && a.getAcquisitionValue().signum() < 0)
      throw new IllegalArgumentException("Acquisition value must be non-negative");
    if (a.getTaxBase() != null && a.getTaxBase().signum() < 0)
      throw new IllegalArgumentException("Tax base must be non-negative");
  }

  private static void validateTaxRate(BigDecimal rate, String label) {
    LongTermAssetRateRules.requireReturnRate(rate, label);
  }
}
