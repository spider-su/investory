package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Loads the related rows needed by both summary and projection reads in one batched shape. */
@Component
public class LongTermAssetRelatedDataLoader {
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final RentalTaxPolicyRepository taxPolicies;
  private final LongTermAssetRentalContractRepository rentalContracts;

  public LongTermAssetRelatedDataLoader(
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      RentalTaxPolicyRepository taxPolicies,
      LongTermAssetRentalContractRepository rentalContracts) {
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.taxPolicies = taxPolicies;
    this.rentalContracts = rentalContracts;
  }

  public Data load(Long portfolioId, List<Long> assetIds, LocalDate date) {
    if (assetIds.isEmpty()) return Data.empty();
    BigDecimal rentalTaxRate =
        taxPolicies.findAllByPortfolioIdOrderByValidFrom(portfolioId).stream()
            .filter(
                policy ->
                    LongTermAssetCalculator.applies(
                        policy.getValidFrom(), policy.getValidTo(), date))
            .findFirst()
            .map(RentalTaxPolicyEntity::getRate)
            .orElse(FinancialPolicyDefaults.RENTAL_TAX_RATE);
    return new Data(
        group(
            rentalContracts.findAllWithTermsByAssetIdIn(assetIds),
            LongTermAssetRentalContractEntity::getAssetId),
        group(
            valuations.findAllByAssetIdInOrderByAssetIdAscValidFromAsc(assetIds),
            LongTermAssetValuationPeriodEntity::getAssetId),
        group(
            bondRates.findAllByAssetIdInOrderByAssetIdAscValidFromAsc(assetIds),
            LongTermAssetBondRatePeriodEntity::getAssetId),
        byId(bonds.findAllById(assetIds), LongTermAssetBondDetailsEntity::getAssetId),
        byId(deposits.findAllById(assetIds), LongTermAssetDepositDetailsEntity::getAssetId),
        rentalTaxRate);
  }

  private static <T> Map<Long, List<T>> group(List<T> rows, Function<T, Long> key) {
    return rows.stream()
        .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
  }

  private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> key) {
    return rows.stream().collect(Collectors.toMap(key, Function.identity()));
  }

  public record Data(
      Map<Long, List<LongTermAssetRentalContractEntity>> contracts,
      Map<Long, List<LongTermAssetValuationPeriodEntity>> valuations,
      Map<Long, List<LongTermAssetBondRatePeriodEntity>> bondRates,
      Map<Long, LongTermAssetBondDetailsEntity> bonds,
      Map<Long, LongTermAssetDepositDetailsEntity> deposits,
      BigDecimal rentalTaxRate) {
    static Data empty() {
      return new Data(
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          FinancialPolicyDefaults.RENTAL_TAX_RATE);
    }
  }
}
