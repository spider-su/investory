package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
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
public class LongTermAssetReadService implements LongTermAssetProfileReader {
  private final LongTermAssetQueryService queries;
  private final LongTermAssetProjectionQueryService projections;
  private final LongTermAssetAnnualSnapshotService annualSnapshots;
  private final CurrencyConversion currencyRates;

  @Override
  public LongTermAssetProfileSnapshotModel snapshot(Long portfolioId, LocalDate date) {
    LongTermAssetProjectionQueryService.Snapshot loaded = projections.snapshot(portfolioId, date);
    List<LongTermAssetSummary> rows =
        loaded == null
            ? queries.list(portfolioId, date)
            : queries.summaries(loaded.assets(), date, loaded.data());
    List<LongTermAssetProfileAssetModel> assets =
        rows.stream()
            .map(
                asset ->
                    new LongTermAssetProfileAssetModel(
                        asset.type(),
                        CurrencyType.USD,
                        toUsd(asset.currentValue(), asset.currency(), date)))
            .toList();
    List<LongTermAssetProjectionModel> projectionInputs =
        (loaded == null ? projections.projectionInputs(portfolioId, date) : loaded.inputs())
            .stream()
                .map(
                    input ->
                        new LongTermAssetProjectionModel(
                            input.id(),
                            input.name(),
                            input.type(),
                            CurrencyType.USD,
                            toUsd(input.currentValue(), input.currency(), date),
                            input.periods().stream()
                                .map(
                                    period ->
                                        new LongTermAssetProjectionModel.Period(
                                            period.validFrom(),
                                            period.validTo(),
                                            toUsd(period.annualIncome(), input.currency(), date),
                                            toUsd(period.annualExpense(), input.currency(), date),
                                            period.annualReturnRate(),
                                            period.cashFlowType(),
                                            period.paidByTenant()))
                                .toList(),
                            input.rentalContracts().stream()
                                .map(
                                    c ->
                                        new RentalContractProjectionModel(
                                            c.id(),
                                            c.startDate(),
                                            c.endDate(),
                                            c.terminatedDate(),
                                            c.rentalTaxPaidByTenant(),
                                            toUsd(c.monthlyTaxBase(), input.currency(), date),
                                            c.terms().stream()
                                                .map(
                                                    t ->
                                                        new RentalContractModel.Term(
                                                            t.type(),
                                                            toUsd(
                                                                t.amount(), input.currency(), date),
                                                            t.frequency(),
                                                            t.paidByTenant()))
                                                .toList()))
                                .toList(),
                            input.maturityDate(),
                            toUsd(input.redemptionValue(), input.currency(), date),
                            input.interestTreatment(),
                            input.taxRate(),
                            toUsd(input.taxBase(), input.currency(), date),
                            input.rentalTaxPaidByTenant()))
                .toList();
    BigDecimal totalValue =
        rows.stream()
            .map(row -> toUsd(row.currentValue(), row.currency(), date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal annualIncome =
        rows.stream()
            .map(
                row -> toUsd(row.annualEconomics().netAnnualIncomeAfterTax(), row.currency(), date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new LongTermAssetProfileSnapshotModel(
        new LongTermAssetProfileSummaryModel(CurrencyType.USD, totalValue, annualIncome),
        assets,
        projectionInputs,
        annualSnapshots.currentAnnualSnapshot(rows, date));
  }

  private BigDecimal toUsd(BigDecimal amount, CurrencyType source, LocalDate date) {
    return amount == null || source == CurrencyType.USD
        ? amount
        : currencyRates.convertToBaseCurrency(amount, CurrencyType.USD, source, date);
  }
}
