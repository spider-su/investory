package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummaryReader;
import com.smartbox.investory.longterm.api.LongTermAssetProjectionReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummarySnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
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
    implements LongTermAssetProfileReader,
        LongTermAssetProfileSummaryReader,
        LongTermAssetProjectionReader {
  private final LongTermAssetQueryService queries;
  private final LongTermAssetProjectionQueryService projections;
  private final LongTermAssetAnnualSnapshotService annualSnapshots;
  private final CurrencyConversion currencyRates;
  private final PortfolioContextReader portfolios;

  @Override
  public LongTermAssetProfileSnapshotModel snapshot(Long portfolioId, LocalDate date) {
    CurrencyType base = baseCurrency(portfolioId);
    LongTermAssetProjectionQueryService.Snapshot loaded = projections.snapshot(portfolioId, date);
    List<LongTermAssetSummary> rows = queries.summaries(loaded.assets(), date, loaded.data());
    ProfileSummary summary = profileSummary(rows, base, date);
    List<LongTermAssetProjectionModel> projectionInputs =
        loaded.inputs().stream().map(input -> toProjectionModel(input, base, date)).toList();
    return new LongTermAssetProfileSnapshotModel(
        summary.model(),
        summary.assets(),
        projectionInputs,
        toBase(annualSnapshots.currentAnnualSnapshot(rows, date), base, date));
  }

  @Override
  public LongTermAssetProfileSummarySnapshotModel summary(Long portfolioId, LocalDate date) {
    CurrencyType base = baseCurrency(portfolioId);
    List<LongTermAssetSummary> rows = queries.list(portfolioId, date);
    ProfileSummary summary = profileSummary(rows, base, date);
    return new LongTermAssetProfileSummarySnapshotModel(
        summary.model(),
        summary.assets(),
        toBase(annualSnapshots.currentAnnualSnapshot(rows, date), base, date));
  }

  @Override
  public List<LongTermAssetProjectionModel> projectionInputs(Long portfolioId, LocalDate date) {
    CurrencyType base = baseCurrency(portfolioId);
    LongTermAssetProjectionQueryService.Snapshot loaded = projections.snapshot(portfolioId, date);
    return loaded.inputs().stream().map(input -> toProjectionModel(input, base, date)).toList();
  }

  private ProfileSummary profileSummary(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
    List<LongTermAssetProfileAssetModel> assets =
        rows.stream()
            .map(
                asset ->
                    new LongTermAssetProfileAssetModel(
                        asset.type(),
                        base,
                        toBase(asset.currentValue(), asset.currency(), base, date)))
            .toList();
    BigDecimal totalValue =
        rows.stream()
            .map(row -> toBase(row.currentValue(), row.currency(), base, date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal annualIncome =
        rows.stream()
            .map(
                row ->
                    toBase(
                        row.annualEconomics().netAnnualIncomeAfterTax(),
                        row.currency(),
                        base,
                        date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ProfileSummary(
        new LongTermAssetProfileSummaryModel(base, totalValue, annualIncome), assets);
  }

  private record ProfileSummary(
      LongTermAssetProfileSummaryModel model, List<LongTermAssetProfileAssetModel> assets) {}

  private LongTermAssetProjectionModel toProjectionModel(
      LongTermAssetProjectionInput input, CurrencyType base, LocalDate date) {
    return new LongTermAssetProjectionModel(
        input.id(),
        input.name(),
        input.type(),
        base,
        toBase(input.currentValue(), input.currency(), base, date),
        input.periods().stream()
            .map(
                period ->
                    new LongTermAssetProjectionModel.Period(
                        period.validFrom(),
                        period.validTo(),
                        toBase(period.annualIncome(), input.currency(), base, date),
                        toBase(period.annualExpense(), input.currency(), base, date),
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
                        toBase(c.monthlyTaxBase(), input.currency(), base, date),
                        c.terms().stream()
                            .map(
                                t ->
                                    new RentalContractModel.Term(
                                        t.type(),
                                        toBase(t.amount(), input.currency(), base, date),
                                        t.frequency(),
                                        t.paidByTenant()))
                            .toList()))
            .toList(),
        input.maturityDate(),
        toBase(input.redemptionValue(), input.currency(), base, date),
        input.interestTreatment(),
        input.taxRate(),
        toBase(input.taxBase(), input.currency(), base, date),
        input.rentalTaxPaidByTenant());
  }

  private CurrencyType baseCurrency(Long portfolioId) {
    return portfolios
        .findById(portfolioId)
        .map(PortfolioContext::baseCurrency)
        .orElse(FinancialPolicyDefaults.CANONICAL_CURRENCY);
  }

  private LongTermAssetAnnualSnapshotModel toBase(
      LongTermAssetAnnualSnapshotModel snapshot, CurrencyType base, LocalDate date) {
    if (snapshot == null || base == FinancialPolicyDefaults.CANONICAL_CURRENCY) return snapshot;
    return new LongTermAssetAnnualSnapshotModel(
        toBase(snapshot.realEstateValue(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date),
        toBase(snapshot.rentalIncome(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date),
        toBase(snapshot.bondValue(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date),
        toBase(snapshot.bondIncome(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date),
        toBase(snapshot.cashReserveValue(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date),
        toBase(snapshot.otherAssetValue(), FinancialPolicyDefaults.CANONICAL_CURRENCY, base, date));
  }

  private BigDecimal toBase(
      BigDecimal amount, CurrencyType source, CurrencyType target, LocalDate date) {
    return amount == null || source == target
        ? amount
        : currencyRates.convertToBaseCurrency(amount, target, source, date);
  }
}
