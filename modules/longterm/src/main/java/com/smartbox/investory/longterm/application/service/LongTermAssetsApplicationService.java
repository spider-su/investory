package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Implements the public Long-Term API and translates internal application read models. */
@Service
@RequiredArgsConstructor
public class LongTermAssetsApplicationService implements LongTermAssetsApi {
  private final LongTermAssetsFacade delegate;

  @Override
  public List<AssetSummaryView> list(Long portfolioId, LocalDate date) {
    return delegate.list(portfolioId, date).stream()
        .map(LongTermAssetsApplicationService::summary)
        .toList();
  }

  @Override
  public List<AssetGroupView> grouped(Long portfolioId, LocalDate date) {
    return delegate.grouped(portfolioId, date).stream()
        .map(LongTermAssetsApplicationService::group)
        .toList();
  }

  @Override
  public AggregateView aggregate(Long portfolioId, LocalDate date) {
    return aggregate(delegate.aggregate(portfolioId, date));
  }

  @Override
  public LongTermAssetsApi.AssetView asset(Long portfolioId, Long id) {
    return asset(delegate.asset(portfolioId, id));
  }

  @Override
  public LongTermAssetsApi.DetailView details(Long portfolioId, Long id, LocalDate date) {
    return detail(delegate.details(portfolioId, id, date));
  }

  @Override
  public LongTermAssetsApi.AssetView create(LongTermAssetsApi.AssetCommand command) {
    return asset(delegate.create(toInternal(command)));
  }

  @Override
  public LongTermAssetsApi.AssetView saveCashReserve(
      LongTermAssetsApi.CashReserveCommand command, LocalDate effectiveFrom) {
    return asset(
        delegate.saveCashReserve(
            new LongTermAssetsFacade.CashReserveCommand(
                command.portfolioId(),
                command.id(),
                command.name(),
                command.currency(),
                command.value(),
                command.annualReturnPercent(),
                command.notes()),
            effectiveFrom));
  }

  @Override
  public LongTermAssetsApi.AssetView createBond(LongTermAssetsApi.BondCommand command) {
    return asset(
        delegate.createBond(
            new LongTermAssetsFacade.BondCommand(
                command.portfolioId(),
                command.id(),
                command.name(),
                command.currency(),
                command.value(),
                command.acquisitionDate(),
                command.maturityDate(),
                interest(command.interestTreatment()),
                command.annualRatePercent(),
                command.notes())));
  }

  @Override
  public void update(LongTermAssetsApi.AssetCommand command) {
    delegate.update(toInternal(command));
  }

  @Override
  public void updateBond(LongTermAssetsApi.BondCommand command) {
    delegate.updateBond(
        new LongTermAssetsFacade.BondCommand(
            command.portfolioId(),
            command.id(),
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.maturityDate(),
            interest(command.interestTreatment()),
            command.annualRatePercent(),
            command.notes()));
  }

  @Override
  public void saveRealEstate(Long portfolioId, RealEstateEntryModel entry) {
    delegate.saveRealEstate(portfolioId, entry);
  }

  @Override
  public LongTermAssetsApi.RentalContractView createRentalContract(
      LongTermAssetsApi.RentalContractCommand command) {
    return rental(delegate.createRentalContract(command));
  }

  @Override
  public LongTermAssetsApi.RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return rental(delegate.endRentalContract(portfolioId, assetId, contractId, endDate));
  }

  @Override
  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate) {
    delegate.terminateRentalContract(portfolioId, assetId, contractId, terminationDate);
  }

  @Override
  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    delegate.saveTaxBase(portfolioId, id, value);
  }

  @Override
  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    delegate.saveRentalTaxOwnership(portfolioId, id, paidByTenant);
  }

  @Override
  public void archive(Long portfolioId, Long id) {
    delegate.archive(portfolioId, id);
  }

  @Override
  public void reactivate(Long portfolioId, Long id) {
    delegate.reactivate(portfolioId, id);
  }

  @Override
  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from) {
    delegate.savePropertyGrowth(portfolioId, id, percent, from);
  }

  @Override
  public void saveBondDetails(
      Long portfolioId, Long id, LongTermAssetsApi.BondDetailsCommand command) {
    delegate.saveBondDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondDetailsCommand(
            command.maturityDate(),
            command.taxRate(),
            interest(command.interestTreatment()),
            command.redemptionValue()));
  }

  @Override
  public void saveDepositDetails(
      Long portfolioId, Long id, LongTermAssetsApi.DepositDetailsCommand command) {
    delegate.saveDepositDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.DepositDetailsCommand(
            command.maturityDate(),
            command.annualInterestRate(),
            command.taxRate(),
            interest(command.interestTreatment())));
  }

  @Override
  public void addValuation(Long portfolioId, Long id, LongTermAssetsApi.ValuationCommand command) {
    delegate.addValuation(
        portfolioId,
        id,
        new LongTermAssetsFacade.ValuationCommand(
            command.validFrom(), command.validTo(), command.growthRatePercent()));
  }

  @Override
  public void addBondRate(Long portfolioId, Long id, LongTermAssetsApi.BondRateCommand command) {
    delegate.addBondRate(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondRateCommand(
            command.validFrom(), command.validTo(), command.annualInterestRate()));
  }

  @Override
  public void saveRentalTaxPolicy(Long portfolioId, LongTermAssetsApi.RentalTaxCommand command) {
    delegate.saveRentalTaxPolicy(
        portfolioId,
        new LongTermAssetsFacade.RentalTaxCommand(
            command.validFrom(), command.validTo(), command.ratePercent(), command.rate()));
  }

  private static LongTermAssetsFacade.AssetCommand toInternal(LongTermAssetsApi.AssetCommand c) {
    return new LongTermAssetsFacade.AssetCommand(
        c.portfolioId(),
        c.id(),
        c.name(),
        assetType(c.type()),
        c.currency(),
        c.acquisitionDate(),
        c.acquisitionValue(),
        c.currentValue(),
        c.taxBase(),
        c.active(),
        c.notes(),
        c.rentalTaxPaidByTenant());
  }

  private static LongTermAssetsApi.AssetView asset(LongTermAssetsFacade.AssetView v) {
    return new LongTermAssetsApi.AssetView(
        v.id(),
        v.portfolioId(),
        v.name(),
        assetType(v.type()),
        v.currency(),
        v.acquisitionDate(),
        v.acquisitionValue(),
        v.currentValue(),
        v.taxBase(),
        v.active(),
        v.notes(),
        v.rentalTaxPaidByTenant());
  }

  private static LongTermAssetsApi.DetailView detail(LongTermAssetsFacade.DetailView v) {
    return new LongTermAssetsApi.DetailView(
        asset(v.asset()),
        summary(v.summary()),
        v.bondDetails() == null ? null : bond(v.bondDetails()),
        v.depositDetails() == null ? null : deposit(v.depositDetails()),
        v.valuationPeriods().stream().map(LongTermAssetsApplicationService::valuation).toList(),
        v.bondRatePeriods().stream().map(LongTermAssetsApplicationService::bondRate).toList(),
        v.expectedPropertyGrowth(),
        v.contracts().stream()
            .map(
                c ->
                    new LongTermAssetsApi.RentalContractView(
                        c.id(),
                        c.startDate(),
                        c.endDate(),
                        c.terminatedDate(),
                        c.rentalTaxPaidByTenant(),
                        c.terms().stream()
                            .map(
                                t ->
                                    new LongTermAssetsApi.RentalTermView(
                                        cashFlowType(t.type()), t.amount(), frequency(t.frequency()), t.paidByTenant()))
                            .toList()))
            .toList());
  }

  private static LongTermAssetsApi.RentalContractView rental(LongTermAssetRentalContractEntity c) {
    return new LongTermAssetsApi.RentalContractView(
        c.getId(),
        c.getStartDate(),
        c.getEndDate(),
        c.getTerminatedDate(),
        c.getRentalTaxPaidByTenant(),
        c.getTerms().stream()
            .map(
                t ->
                    new LongTermAssetsApi.RentalTermView(
                        cashFlowType(t.getType()), t.getAmount(), frequency(t.getFrequency()), t.isPaidByTenant()))
            .toList());
  }

  private static AssetSummaryView summary(LongTermAssetSummary s) {
    return new AssetSummaryView(
        s.id(),
        s.name(),
        assetType(s.type()),
        s.currency(),
        s.currentValue(),
        s.maturityDate(),
        s.currentAnnualRate(),
        economics(s.annualEconomics()),
        s.realEstatePlanning() == null ? null : realEstate(s.realEstatePlanning()),
        s.bondPlanning() == null ? null : bondPlanning(s.bondPlanning()),
        s.rentEnd());
  }

  private static AssetGroupView group(LongTermAssetService.AssetGroupSummary g) {
    return new AssetGroupView(
        g.key(),
        g.title(),
        g.currency(),
        g.assets().stream().map(LongTermAssetsApplicationService::summary).toList(),
        g.totalValue(),
        economics(g.annualEconomics()),
        g.realEstatePlanning() == null
            ? null
            : new RealEstateGroupPlanningView(
                g.realEstatePlanning().totalPaymentMonthly(),
                g.realEstatePlanning().netMonthlyIncome(),
                g.realEstatePlanning().monthlyRentTax(),
                g.realEstatePlanning().incomeYield()));
  }

  private static AggregateView aggregate(LongTermAssetService.AggregateSummary a) {
    return new AggregateView(a.currency(), a.totalCurrentValue(), economics(a.annualEconomics()));
  }

  private static AnnualEconomicsView economics(AnnualEconomics e) {
    return new AnnualEconomicsView(
        e.grossAnnualIncome(),
        e.annualExpenses(),
        e.annualTax(),
        e.netAnnualIncomeBeforeTax(),
        e.netAnnualIncomeAfterTax(),
        e.grossYield(),
        e.netYieldBeforeTax(),
        e.netYieldAfterTax());
  }

  private static RealEstatePlanningView realEstate(RealEstatePlanningSummary p) {
    return new RealEstatePlanningView(
        p.taxBase(),
        p.totalPaymentMonthly(),
        p.monthlyIncome(),
        p.monthlyReduce(),
        p.annualTax(),
        p.netMonthlyIncome(),
        p.incomeYield());
  }

  private static BondPlanningView bondPlanning(BondPlanningSummary p) {
    return new BondPlanningView(
        p.value(),
        p.annualRate(),
        p.grossInterest(),
        p.annualTax(),
        p.netInterest(),
        p.netYield(),
        p.maturityDate(),
        interest(p.interestTreatment()));
  }

  private static LongTermAssetsApi.BondDetailsView bond(LongTermAssetsFacade.BondDetailsView d) {
    return new LongTermAssetsApi.BondDetailsView(
        d.maturityDate(), d.taxRate(), interest(d.interestTreatment()), d.redemptionValue());
  }

  private static LongTermAssetsApi.DepositDetailsView deposit(
      LongTermAssetsFacade.DepositDetailsView d) {
    return new LongTermAssetsApi.DepositDetailsView(
        d.maturityDate(), d.annualInterestRate(), d.taxRate(), interest(d.interestTreatment()));
  }

  private static LongTermAssetsApi.ValuationView valuation(LongTermAssetsFacade.ValuationView p) {
    return new LongTermAssetsApi.ValuationView(
        p.validFrom(), p.validTo(), p.expectedAnnualGrowthRate());
  }

  private static LongTermAssetsApi.BondRateView bondRate(LongTermAssetsFacade.BondRateView p) {
    return new LongTermAssetsApi.BondRateView(p.validFrom(), p.validTo(), p.annualInterestRate());
  }

  private static com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType assetType(LongTermAssetTypeModel value) { return com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.valueOf(value.name()); }
  private static LongTermAssetTypeModel assetType(com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType value) { return LongTermAssetTypeModel.valueOf(value.name()); }
  private static com.smartbox.investory.longterm.infrastructure.rental.CashFlowType cashFlowType(CashFlowTypeModel value) { return com.smartbox.investory.longterm.infrastructure.rental.CashFlowType.valueOf(value.name()); }
  private static CashFlowTypeModel cashFlowType(com.smartbox.investory.longterm.infrastructure.rental.CashFlowType value) { return CashFlowTypeModel.valueOf(value.name()); }
  private static com.smartbox.investory.longterm.infrastructure.rental.Frequency frequency(FrequencyModel value) { return com.smartbox.investory.longterm.infrastructure.rental.Frequency.valueOf(value.name()); }
  private static FrequencyModel frequency(com.smartbox.investory.longterm.infrastructure.rental.Frequency value) { return FrequencyModel.valueOf(value.name()); }
  private static com.smartbox.investory.longterm.infrastructure.InterestTreatment interest(InterestTreatmentModel value) { return com.smartbox.investory.longterm.infrastructure.InterestTreatment.valueOf(value.name()); }
  private static InterestTreatmentModel interest(com.smartbox.investory.longterm.infrastructure.InterestTreatment value) { return InterestTreatmentModel.valueOf(value.name()); }
}
