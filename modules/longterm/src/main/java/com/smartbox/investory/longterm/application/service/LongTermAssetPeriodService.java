package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns validation and replacement rules for effective-dated rates, growth, and tax policies. */
@Service
@Transactional
public class LongTermAssetPeriodService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final RentalTaxPolicyRepository taxPolicies;

  public LongTermAssetPeriodService(
      LongTermAssetRepository assets,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      RentalTaxPolicyRepository taxPolicies) {
    this.assets = assets;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.taxPolicies = taxPolicies;
  }

  public RentalTaxPolicyEntity saveRentalTaxPolicy(Long portfolioId, RentalTaxPolicyEntity policy) {
    if (policy.getId() != null)
      taxPolicies
          .findById(policy.getId())
          .filter(existing -> Objects.equals(existing.getPortfolioId(), portfolioId))
          .orElseThrow(() -> new NoSuchElementException("Rental tax policy not found"));
    policy.setPortfolioId(portfolioId);
    validateRange(policy.getValidFrom(), policy.getValidTo());
    if (policy.getRate() == null
        || policy.getRate().signum() < 0
        || policy.getRate().compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Tax rate must be between 0 and 1");
    for (RentalTaxPolicyEntity existing :
        taxPolicies.findAllByPortfolioIdOrderByValidFrom(portfolioId))
      if (Objects.equals(existing.getPortfolioId(), portfolioId)
          && !Objects.equals(existing.getId(), policy.getId())
          && rangesOverlap(
              policy.getValidFrom(),
              policy.getValidTo(),
              existing.getValidFrom(),
              existing.getValidTo()))
        throw new IllegalArgumentException("Overlapping rental tax policies are not allowed");
    return taxPolicies.save(policy);
  }

  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    RentalTaxPolicyEntity policy =
        taxPolicies
            .findById(policyId)
            .filter(existing -> Objects.equals(existing.getPortfolioId(), portfolioId))
            .orElseThrow(() -> new NoSuchElementException("Rental tax policy not found"));
    taxPolicies.delete(policy);
  }

  public void replaceValuationGrowth(Long assetId, LocalDate from, BigDecimal growthRate) {
    if (growthRate != null) validateGrowthRate(growthRate);
    if (growthRate == null) {
      closeValuationPeriods(assetId, from);
      return;
    }
    saveOpenValuationPeriod(assetId, from, growthRate);
  }

  public void replaceCurrentValuationGrowth(Long assetId, LocalDate from, BigDecimal growthRate) {
    if (growthRate != null) validateGrowthRate(growthRate);
    var existing = valuations.findAllByAssetIdOrderByValidFrom(assetId);
    if (!existing.isEmpty()) {
      valuations.deleteAll(existing);
      valuations.flush();
    }
    if (growthRate != null) {
      var period = new LongTermAssetValuationPeriodEntity();
      period.setAssetId(assetId);
      period.setValidFrom(from);
      period.setValidTo(null);
      period.setExpectedAnnualGrowthRate(growthRate);
      valuations.save(period);
    }
  }

  public void saveExpectedPropertyGrowth(
      Long portfolioId, Long assetId, BigDecimal growthRate, LocalDate effectiveFrom) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("AssetEntity is not real estate");
    replaceValuationGrowth(assetId, effectiveFrom, growthRate);
  }

  public LongTermAssetValuationPeriodEntity addValuationPeriod(
      Long portfolioId, Long assetId, LongTermAssetValuationPeriodEntity period) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Valuation periods apply only to real estate");
    validateRange(period.getValidFrom(), period.getValidTo());
    validateGrowthRate(period.getExpectedAnnualGrowthRate());
    validateValuationOverlap(assetId, period);
    period.setAssetId(assetId);
    return valuations.save(period);
  }

  public LongTermAssetValuationPeriodEntity updateValuationPeriod(
      Long portfolioId,
      Long assetId,
      Long periodId,
      LongTermAssetValuationPeriodEntity replacement) {
    LongTermAssetValuationPeriodEntity period = ownedValuation(portfolioId, assetId, periodId);
    period.setValidFrom(replacement.getValidFrom());
    period.setValidTo(replacement.getValidTo());
    period.setExpectedAnnualGrowthRate(replacement.getExpectedAnnualGrowthRate());
    validateRange(period.getValidFrom(), period.getValidTo());
    validateGrowthRate(period.getExpectedAnnualGrowthRate());
    validateValuationOverlap(assetId, period);
    return valuations.save(period);
  }

  public void deleteValuationPeriod(Long portfolioId, Long assetId, Long periodId) {
    valuations.delete(ownedValuation(portfolioId, assetId, periodId));
  }

  public void replaceBondRate(
      Long assetId, LocalDate from, LocalDate maturityDate, BigDecimal rate) {
    var existing = bondRates.findAllByAssetIdOrderByValidFrom(assetId);
    if (!existing.isEmpty()) {
      bondRates.deleteAll(existing);
      bondRates.flush();
    }
    LongTermAssetBondRatePeriodEntity period = new LongTermAssetBondRatePeriodEntity();
    period.setAssetId(assetId);
    period.setValidFrom(from);
    period.setValidTo(maturityDate);
    period.setAnnualInterestRate(rate);
    saveBondRatePeriod(assetId, period);
  }

  private void saveBondRatePeriod(Long assetId, LongTermAssetBondRatePeriodEntity period) {
    validateRange(period.getValidFrom(), period.getValidTo());
    if (period.getAnnualInterestRate() == null || period.getAnnualInterestRate().signum() < 0)
      throw new IllegalArgumentException("Bond interest rate must be non-negative");
    period.setAssetId(assetId);
    bondRates.save(period);
  }

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private LongTermAssetValuationPeriodEntity ownedValuation(
      Long portfolioId, Long assetId, Long periodId) {
    owned(portfolioId, assetId);
    return valuations
        .findById(periodId)
        .filter(period -> Objects.equals(period.getAssetId(), assetId))
        .orElseThrow(() -> new NoSuchElementException("Valuation period not found"));
  }

  private static void validateGrowthRate(BigDecimal rate) {
    if (rate == null
        || rate.compareTo(BigDecimal.ONE.negate()) < 0
        || rate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Expected property growth rate must be between -1 and 1");
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || (to != null && to.isBefore(from)))
      throw new IllegalArgumentException("Invalid period");
  }

  private void closeValuationPeriods(Long id, LocalDate from) {
    for (LongTermAssetValuationPeriodEntity old :
        valuations.findAllByAssetIdOrderByValidFrom(id).stream()
            .filter(period -> period.getValidTo() == null || !period.getValidTo().isBefore(from))
            .toList()) {
      if (old.getValidFrom().isBefore(from)) {
        old.setValidTo(from.minusDays(1));
        valuations.save(old);
      } else if (old.getValidFrom().isAfter(from)) {
        valuations.delete(old);
      }
    }
  }

  private void saveOpenValuationPeriod(Long assetId, LocalDate from, BigDecimal growthRate) {
    closeValuationPeriods(assetId, from);
    LongTermAssetValuationPeriodEntity period =
        valuations.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(existing -> existing.getValidFrom().equals(from))
            .findFirst()
            .orElseGet(LongTermAssetValuationPeriodEntity::new);
    period.setAssetId(assetId);
    period.setValidFrom(from);
    period.setValidTo(null);
    period.setExpectedAnnualGrowthRate(growthRate);
    valuations.save(period);
  }

  private void validateValuationOverlap(Long id, LongTermAssetValuationPeriodEntity period) {
    if (valuations.findAllByAssetIdOrderByValidFrom(id).stream()
        .filter(
            existing -> period.getId() == null || !Objects.equals(existing.getId(), period.getId()))
        .anyMatch(
            existing ->
                rangesOverlap(
                    existing.getValidFrom(),
                    existing.getValidTo(),
                    period.getValidFrom(),
                    period.getValidTo())))
      throw new IllegalArgumentException("Overlapping valuation period");
  }

  private static boolean rangesOverlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return LongTermAssetPeriodRules.overlaps(a, b, c, d);
  }
}
