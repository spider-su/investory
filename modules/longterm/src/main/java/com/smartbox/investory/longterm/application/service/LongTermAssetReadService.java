package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
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
  private final LongTermAssetService longTermAssets;
  private final CurrencyConversion currencyRates;

  @Override
  public LongTermAssetProfileSummaryModel aggregate(Long portfolioId, LocalDate date) {
    LongTermAssetQueryService.AggregateSummary summary =
        longTermAssets.aggregate(portfolioId, date);
    return new LongTermAssetProfileSummaryModel(
        CurrencyType.USD,
        toUsd(summary.totalCurrentValue(), summary.currency(), date),
        toUsd(summary.annualEconomics().netAnnualIncomeAfterTax(), summary.currency(), date));
  }

  @Override
  public List<LongTermAssetProfileAssetModel> list(Long portfolioId, LocalDate date) {
    return longTermAssets.list(portfolioId, date).stream()
        .map(
            asset ->
                new LongTermAssetProfileAssetModel(
                    LongTermAssetTypeModel.valueOf(asset.type().name()),
                    CurrencyType.USD,
                    toUsd(asset.currentValue(), asset.currency(), date)))
        .toList();
  }

  @Override
  public List<LongTermAssetProjectionModel> projectionInputs(Long portfolioId, LocalDate date) {
    return longTermAssets.projectionInputs(portfolioId, date).stream()
        .map(
            input ->
                new LongTermAssetProjectionModel(
                    input.id(),
                    input.name(),
                    LongTermAssetTypeModel.valueOf(input.type().name()),
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
                                    period.cashFlowType() == null
                                        ? null
                                        : CashFlowTypeModel.valueOf(period.cashFlowType().name()),
                                    period.paidByTenant()))
                        .toList(),
                    input.rentalContracts().stream()
                        .map(
                            c ->
                                new com.smartbox.investory.longterm.api.model.RentalContractModel(
                                    c.id(),
                                    c.startDate(),
                                    c.endDate(),
                                    c.terminatedDate(),
                                    c.rentalTaxPaidByTenant(),
                                    toUsd(c.monthlyTaxBase(), input.currency(), date),
                                    c.tenantName(),
                                    c.tenantEmail(),
                                    c.tenantPhone(),
                                    c.terms().stream()
                                        .map(
                                            t ->
                                                new com.smartbox.investory.longterm.api.model
                                                    .RentalContractModel.Term(
                                                    CashFlowTypeModel.valueOf(t.type().name()),
                                                    toUsd(t.amount(), input.currency(), date),
                                                    FrequencyModel.valueOf(t.frequency().name()),
                                                    t.paidByTenant()))
                                        .toList()))
                        .toList(),
                    input.maturityDate(),
                    toUsd(input.redemptionValue(), input.currency(), date),
                    input.interestTreatment() == null
                        ? null
                        : InterestTreatmentModel.valueOf(input.interestTreatment().name()),
                    input.taxRate(),
                    toUsd(input.taxBase(), input.currency(), date),
                    input.rentalTaxPaidByTenant()))
        .toList();
  }

  private BigDecimal toUsd(BigDecimal amount, CurrencyType source, LocalDate date) {
    return amount == null || source == CurrencyType.USD
        ? amount
        : currencyRates.convertToBaseCurrency(amount, CurrencyType.USD, source, date);
  }
}
