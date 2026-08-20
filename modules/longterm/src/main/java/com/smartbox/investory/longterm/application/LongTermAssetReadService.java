package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileAsset;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummary;
import com.smartbox.investory.longterm.api.LongTermAssetProjection;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts Long-Term application calculations to persistence-free public read contracts. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LongTermAssetReadService
    implements LongTermAssetProfileReader, LongTermAssetAnnualSnapshotReader {
  private final LongTermAssetService longTermAssets;
  private final CurrencyConversion currencyRates;

  @Override
  public LongTermAssetProfileSummary aggregate(Long portfolioId, LocalDate date) {
    LongTermAssetService.AggregateSummary summary = longTermAssets.aggregate(portfolioId, date);
    return new LongTermAssetProfileSummary(
        CurrencyType.USD,
        toUsd(summary.totalCurrentValue(), summary.currency(), date),
        toUsd(summary.annualEconomics().netAnnualIncomeAfterTax(), summary.currency(), date));
  }

  @Override
  public List<LongTermAssetProfileAsset> list(Long portfolioId, LocalDate date) {
    return longTermAssets.list(portfolioId, date).stream()
        .map(
            asset ->
                new LongTermAssetProfileAsset(
                    asset.type(),
                    CurrencyType.USD,
                    toUsd(asset.currentValue(), asset.currency(), date)))
        .toList();
  }

  @Override
  public List<LongTermAssetProjection> projectionInputs(Long portfolioId, LocalDate date) {
    return longTermAssets.projectionInputs(portfolioId, date).stream()
        .map(
            input ->
                new LongTermAssetProjection(
                    input.id(),
                    input.name(),
                    input.type(),
                    CurrencyType.USD,
                    toUsd(input.currentValue(), input.currency(), date),
                    input.periods().stream()
                        .map(
                            period ->
                                new LongTermAssetProjection.Period(
                                    period.validFrom(),
                                    period.validTo(),
                                    toUsd(period.annualIncome(), input.currency(), date),
                                    toUsd(period.annualExpense(), input.currency(), date),
                                    period.annualReturnRate(),
                                    period.cashFlowType()))
                        .toList(),
                    input.maturityDate(),
                    toUsd(input.redemptionValue(), input.currency(), date),
                    input.interestTreatment(),
                    input.taxRate(),
                    toUsd(input.taxBase(), input.currency(), date)))
        .toList();
  }

  @Override
  public LongTermAssetAnnualSnapshot historicalAnnualSnapshot(Long portfolioId, int year) {
    return longTermAssets.historicalAnnualSnapshot(portfolioId, year);
  }

  @Override
  public LongTermAssetAnnualSnapshot currentAnnualSnapshot(Long portfolioId, LocalDate date) {
    return longTermAssets.currentAnnualSnapshot(portfolioId, date);
  }

  private BigDecimal toUsd(BigDecimal amount, CurrencyType source, LocalDate date) {
    return amount == null || source == CurrencyType.USD
        ? amount
        : currencyRates.convertToBaseCurrency(amount, CurrencyType.USD, source, date);
  }
}
