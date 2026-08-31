package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetAnnualSnapshotService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetRentalContractRepository rentalContracts;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetQueryService queries;

  public LongTermAssetAnnualSnapshotService(
      LongTermAssetRepository assets,
      LongTermAssetCashFlowRepository cashFlows,
      LongTermAssetRentalContractRepository rentalContracts,
      CurrencyConversion currencyRates,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetQueryService queries) {
    this.assets = assets;
    this.cashFlows = cashFlows;
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
    Set<Long> historicalAssetIds =
        assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
            .filter(
                asset ->
                    (asset.getAcquisitionDate() == null
                            || !asset.getAcquisitionDate().isAfter(date))
                        && lifecycle.activeOn(asset, date))
            .map(LongTermAssetEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
    List<LongTermAssetSummary> rows =
        assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
            .filter(asset -> historicalAssetIds.contains(asset.getId()))
            .map(asset -> queries.summary(asset, date))
            .filter(row -> historicalAssetIds.contains(row.id()))
            .toList();
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
                  return row.currency() == CurrencyType.USD
                      ? amount
                      : currencyRates.convertToBaseCurrency(
                          amount, CurrencyType.USD, row.currency(), date);
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal bondIncome = sumSpendableBondIncome(rows, date);
    boolean hasRealEstate =
        rows.stream().anyMatch(row -> row.type() == LongTermAssetType.REAL_ESTATE);
    boolean rentalDataComplete =
        rows.stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .allMatch(
                row ->
                    cashFlows.findAllByAssetIdOrderByValidFrom(row.id()).stream()
                            .anyMatch(flow -> !flow.getValidFrom().isAfter(date))
                        || rentalContracts.findAllByAssetIdOrderByStartDate(row.id()).stream()
                            .anyMatch(contract -> !contract.getStartDate().isAfter(date)));
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
    List<LongTermAssetSummary> rows = queries.list(portfolioId, date);
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
    BigDecimal otherAssetValue = sumCanonical(rows, null, LongTermAssetSummary::currentValue, date);
    return new LongTermAssetAnnualSnapshotModel(
        realEstateValue, rentalIncome, bondValue, bondIncome, cashReserveValue, otherAssetValue);
  }

  private BigDecimal sumCanonical(
      List<LongTermAssetSummary> rows,
      LongTermAssetType type,
      java.util.function.Function<LongTermAssetSummary, BigDecimal> value,
      LocalDate date) {
    return rows.stream()
        .filter(
            row ->
                type == null
                    ? row.type() == LongTermAssetType.DEPOSIT
                        || row.type() == LongTermAssetType.OTHER
                    : row.type() == type)
        .map(
            row -> {
              BigDecimal amount = value.apply(row);
              if (amount == null) return BigDecimal.ZERO;
              return row.currency() == CurrencyType.USD
                  ? amount
                  : currencyRates.convertToBaseCurrency(
                      amount, CurrencyType.USD, row.currency(), date);
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
              BigDecimal amount = row.netAnnualIncomeAfterTax();
              if (amount == null) return BigDecimal.ZERO;
              return row.currency() == CurrencyType.USD
                  ? amount
                  : currencyRates.convertToBaseCurrency(
                      amount, CurrencyType.USD, row.currency(), date);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
