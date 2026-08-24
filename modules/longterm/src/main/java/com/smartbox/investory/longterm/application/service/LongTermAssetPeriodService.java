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

  public void replaceValuationGrowth(Long assetId, LocalDate from, BigDecimal growthRate) {
    if (growthRate != null) validateGrowthRate(growthRate);
    if (growthRate == null) {
      closeValuationPeriods(assetId, from);
      return;
    }
    saveOpenValuationPeriod(assetId, from, growthRate);
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
    if (asset.getType() != LongTermAssetType.REAL_ESTATE
        && asset.getType() != LongTermAssetType.CASH_RESERVE)
      throw new IllegalArgumentException(
          "Valuation periods apply only to real estate or cash reserve");
    validateRange(period.getValidFrom(), period.getValidTo());
    validateGrowthRate(period.getExpectedAnnualGrowthRate());
    validateValuationOverlap(assetId, period);
    period.setAssetId(assetId);
    return valuations.save(period);
  }

  public LongTermAssetBondRatePeriodEntity addBondRatePeriod(
      Long portfolioId, Long assetId, LongTermAssetBondRatePeriodEntity period) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("Bond-rate periods apply only to bonds");
    saveBondRatePeriod(assetId, period, true);
    return period;
  }

  public void replaceBondRate(
      Long assetId, LocalDate from, LocalDate maturityDate, BigDecimal rate) {
    for (LongTermAssetBondRatePeriodEntity existing :
        bondRates.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(period -> period.getId() == null || !period.getValidFrom().equals(from))
            .filter(period -> period.getValidTo() == null || !period.getValidTo().isBefore(from))
            .toList()) {
      if (existing.getValidFrom().isBefore(from)) {
        existing.setValidTo(from.minusDays(1));
        bondRates.save(existing);
      } else {
        bondRates.delete(existing);
      }
    }
    LongTermAssetBondRatePeriodEntity period =
        bondRates.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(existing -> existing.getValidFrom().equals(from))
            .findFirst()
            .orElseGet(LongTermAssetBondRatePeriodEntity::new);
    period.setAssetId(assetId);
    period.setValidFrom(from);
    period.setValidTo(maturityDate);
    period.setAnnualInterestRate(rate);
    saveBondRatePeriod(assetId, period, period.getId() == null);
  }

  private void saveBondRatePeriod(
      Long assetId, LongTermAssetBondRatePeriodEntity period, boolean validateOverlap) {
    validateRange(period.getValidFrom(), period.getValidTo());
    if (period.getAnnualInterestRate() == null || period.getAnnualInterestRate().signum() < 0)
      throw new IllegalArgumentException("Bond interest rate must be non-negative");
    if (validateOverlap) validateBondRateOverlap(assetId, period);
    period.setAssetId(assetId);
    bondRates.save(period);
  }

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
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
        .anyMatch(
            existing ->
                rangesOverlap(
                    existing.getValidFrom(),
                    existing.getValidTo(),
                    period.getValidFrom(),
                    period.getValidTo())))
      throw new IllegalArgumentException("Overlapping valuation period");
  }

  private void validateBondRateOverlap(Long id, LongTermAssetBondRatePeriodEntity period) {
    if (bondRates.findAllByAssetIdOrderByValidFrom(id).stream()
        .anyMatch(
            existing ->
                rangesOverlap(
                    existing.getValidFrom(),
                    existing.getValidTo(),
                    period.getValidFrom(),
                    period.getValidTo())))
      throw new IllegalArgumentException("Overlapping bond-rate period");
  }

  private static boolean rangesOverlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return LongTermAssetPeriodRules.overlaps(a, b, c, d);
  }
}
