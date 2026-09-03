package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Owns portfolio population and the batched related-data load used by read models. */
@Component
public class LongTermAssetPopulationLoader {
  private final LongTermAssetRepository assets;
  private final LongTermAssetRelatedDataLoader relatedData;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final RentalTaxPolicyRepository taxPolicies;
  private final LongTermAssetRentalContractRepository rentalContracts;

  public LongTermAssetPopulationLoader(
      LongTermAssetRepository assets,
      LongTermAssetRelatedDataLoader relatedData,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetValuationPeriodRepository valuations,
      RentalTaxPolicyRepository taxPolicies,
      LongTermAssetRentalContractRepository rentalContracts) {
    this.assets = assets;
    this.relatedData = relatedData;
    this.lifecycle = lifecycle;
    this.valuations = valuations;
    this.taxPolicies = taxPolicies;
    this.rentalContracts = rentalContracts;
  }

  public List<LongTermAssetEntity> activeAt(Long portfolioId, LocalDate date) {
    return lifecycle.activeAt(assets.findAllByPortfolioIdOrderByName(portfolioId), date);
  }

  public LongTermAssetRelatedDataLoader.Data relatedData(
      Long portfolioId, List<LongTermAssetEntity> rows, LocalDate date) {
    if (rows.isEmpty()) return LongTermAssetRelatedDataLoader.Data.empty();
    return relatedData.load(
        portfolioId, rows.stream().map(LongTermAssetEntity::getId).toList(), date);
  }

  public static List<Long> idsOfType(List<LongTermAssetEntity> rows, LongTermAssetType type) {
    return rows.stream()
        .filter(row -> row.getType() == type)
        .map(LongTermAssetEntity::getId)
        .toList();
  }

  public List<LongTermAssetEntity> archived(Long portfolioId) {
    return assets.findAllByPortfolioIdAndActiveFalseOrderByName(portfolioId);
  }

  public Optional<LongTermAssetEntity> find(Long portfolioId, Long id) {
    return assets.findByIdAndPortfolioId(id, portfolioId);
  }

  public LongTermAssetValuationPeriodEntity valuation(
      Long portfolioId, Long assetId, Long periodId) {
    owned(portfolioId, assetId);
    return valuations
        .findById(periodId)
        .filter(period -> Objects.equals(period.getAssetId(), assetId))
        .orElseThrow(() -> new ValuationNotFoundException(assetId, periodId));
  }

  public LongTermAssetRentalContractEntity rentalContract(
      Long portfolioId, Long assetId, Long contractId) {
    owned(portfolioId, assetId);
    return rentalContracts
        .findById(contractId)
        .filter(contract -> Objects.equals(contract.getAssetId(), assetId))
        .orElseThrow(() -> new RentalContractNotFoundException(assetId, contractId));
  }

  public RentalTaxPolicyEntity rentalTaxPolicy(Long portfolioId, Long policyId) {
    return taxPolicies
        .findById(policyId)
        .filter(policy -> Objects.equals(policy.getPortfolioId(), portfolioId))
        .orElseThrow(() -> new RentalTaxPolicyNotFoundException(portfolioId, policyId));
  }

  public Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    return taxPolicies
        .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToGreaterThanEqualOrderByValidFromDesc(
            portfolioId, date, date)
        .or(
            () ->
                taxPolicies
                    .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
                        portfolioId, date));
  }

  public List<RentalTaxPolicyEntity> rentalTaxPolicies(Long portfolioId) {
    return taxPolicies.findAllByPortfolioIdOrderByValidFrom(portfolioId);
  }

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return find(portfolioId, id).orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
  }
}
