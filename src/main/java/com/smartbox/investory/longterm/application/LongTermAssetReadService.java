package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileAsset;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummary;
import com.smartbox.investory.longterm.api.LongTermAssetProjection;
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

  @Override
  public LongTermAssetProfileSummary aggregate(Long portfolioId, LocalDate date) {
    LongTermAssetService.AggregateSummary summary = longTermAssets.aggregate(portfolioId, date);
    return new LongTermAssetProfileSummary(
        summary.currency(),
        summary.totalCurrentValue(),
        summary.annualEconomics().netAnnualIncomeAfterTax());
  }

  @Override
  public List<LongTermAssetProfileAsset> list(Long portfolioId, LocalDate date) {
    return longTermAssets.list(portfolioId, date).stream()
        .map(
            asset ->
                new LongTermAssetProfileAsset(asset.type(), asset.currency(), asset.currentValue()))
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
                    input.currency(),
                    input.currentValue(),
                    input.periods().stream()
                        .map(
                            period ->
                                new LongTermAssetProjection.Period(
                                    period.validFrom(),
                                    period.validTo(),
                                    period.annualIncome(),
                                    period.annualExpense(),
                                    period.annualReturnRate(),
                                    period.cashFlowType()))
                        .toList(),
                    input.maturityDate(),
                    input.redemptionValue(),
                    input.interestTreatment(),
                    input.taxRate(),
                    input.taxBase()))
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
}
