package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetAnnualSnapshotService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetRentalContractRepository rentalContracts;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetQueryService queries;

  public LongTermAssetAnnualSnapshotService(
      LongTermAssetRepository assets,
      LongTermAssetRentalContractRepository rentalContracts,
      CurrencyConversion currencyRates,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetQueryService queries) {
    this.assets = assets;
    this.rentalContracts = rentalContracts;
    this.currencyRates = currencyRates;
    this.lifecycle = lifecycle;
    this.queries = queries;
  }

  /**
   * Returns the same normalized annual economics used by the Long-term Assets overview.
   *
   * <p>Current values are deliberately not exposed as historical values: this model has no dated
   * valuation table from which a prior Dec-31 balance could be proven.
   */
  /** Returns historical Long-Term facts in the canonical USD application currency. */
  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year) {
    LocalDate date = LocalDate.of(year, 12, 31);
    List<LongTermAssetEntity> historicalAssets =
        assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
            .filter(
                asset ->
                    (asset.getAcquisitionDate() == null
                            || !asset.getAcquisitionDate().isAfter(date))
                        && lifecycle.activeOn(asset, date))
            .toList();
    List<LongTermAssetSummary> rows = queries.summaries(historicalAssets, date);
    BigDecimal rentalIncome =
        rows.stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .map(
                row -> {
                  BigDecimal amount;
                  // Historical snapshots expose the annual economics effective at the
                  // boundary date.  They intentionally do not prorate a period that began
                  // during the requested year; that is the established snapshot contract and
                  // keeps historical planning aligned with the asset summary.
                  amount = row.annualEconomics().netAnnualIncomeAfterTax();
                  return row.currency() == FinancialPolicyDefaults.CANONICAL_CURRENCY
                      ? amount
                      : currencyRates.convertToBaseCurrency(
                          amount, FinancialPolicyDefaults.CANONICAL_CURRENCY, row.currency(), date);
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal bondIncome = sumSpendableBondIncome(rows, date);
    boolean hasRealEstate =
        rows.stream().anyMatch(row -> row.type() == LongTermAssetType.REAL_ESTATE);
    Set<Long> realEstateIds =
        rows.stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .map(LongTermAssetSummary::id)
            .collect(java.util.stream.Collectors.toSet());
    Set<Long> assetsWithRentalHistory =
        realEstateIds.isEmpty()
            ? Set.of()
            : rentalContracts.findAllWithTermsByAssetIdIn(realEstateIds).stream()
                .filter(contract -> !contract.getStartDate().isAfter(date))
                .map(LongTermAssetRentalContractEntity::getAssetId)
                .collect(java.util.stream.Collectors.toSet());
    boolean rentalDataComplete = assetsWithRentalHistory.containsAll(realEstateIds);
    return new LongTermAssetAnnualSnapshotModel(
        null,
        !hasRealEstate || rentalDataComplete ? rentalIncome : null,
        null,
        bondIncome,
        null,
        null);
  }

  /** Returns current Long-Term facts in the canonical USD application currency. */
  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel currentAnnualSnapshot(Long portfolioId, LocalDate date) {
    return currentAnnualSnapshot(queries.list(portfolioId, date), date);
  }

  /** Builds the current snapshot from already loaded rows to keep composed reads coherent. */
  public LongTermAssetAnnualSnapshotModel currentAnnualSnapshot(
      List<LongTermAssetSummary> rows, LocalDate date) {
    BigDecimal realEstateValue =
        sumCanonical(rows, LongTermAssetType.REAL_ESTATE, LongTermAssetSummary::currentValue, date);
    BigDecimal rentalIncome =
        sumCanonical(
            rows,
            LongTermAssetType.REAL_ESTATE,
            r -> r.annualEconomics().netAnnualIncomeAfterTax(),
            date);
    BigDecimal bondValue =
        sumCanonical(rows, LongTermAssetType.BOND, LongTermAssetSummary::currentValue, date);
    BigDecimal bondIncome = sumSpendableBondIncome(rows, date);
    BigDecimal cashReserveValue =
        sumCanonical(
            rows, LongTermAssetType.CASH_RESERVE, LongTermAssetSummary::currentValue, date);
    BigDecimal otherAssetValue =
        sumCanonical(rows, LongTermAssetType.DEPOSIT, LongTermAssetSummary::currentValue, date);
    return new LongTermAssetAnnualSnapshotModel(
        realEstateValue, rentalIncome, bondValue, bondIncome, cashReserveValue, otherAssetValue);
  }

  private BigDecimal sumCanonical(
      List<LongTermAssetSummary> rows,
      LongTermAssetType type,
      java.util.function.Function<LongTermAssetSummary, BigDecimal> value,
      LocalDate date) {
    return rows.stream()
        .filter(row -> type == null ? row.type().contributesToCalculations() : row.type() == type)
        .map(
            row -> {
              BigDecimal amount = value.apply(row);
              if (amount == null) return BigDecimal.ZERO;
              return row.currency() == FinancialPolicyDefaults.CANONICAL_CURRENCY
                  ? amount
                  : currencyRates.convertToBaseCurrency(
                      amount, FinancialPolicyDefaults.CANONICAL_CURRENCY, row.currency(), date);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumSpendableBondIncome(List<LongTermAssetSummary> rows, LocalDate date) {
    return rows.stream()
        .filter(row -> row.type() == LongTermAssetType.BOND)
        .filter(
            row ->
                row.bondPlanning() == null
                    || row.bondPlanning().interestTreatment() != InterestTreatment.CAPITALIZE)
        .map(
            row -> {
              BigDecimal amount = row.annualEconomics().netAnnualIncomeAfterTax();
              if (amount == null) return BigDecimal.ZERO;
              return row.currency() == FinancialPolicyDefaults.CANONICAL_CURRENCY
                  ? amount
                  : currencyRates.convertToBaseCurrency(
                      amount, FinancialPolicyDefaults.CANONICAL_CURRENCY, row.currency(), date);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
