package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.*;
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
  public AssetView asset(Long portfolioId, Long id) {
    return asset(delegate.asset(portfolioId, id));
  }

  @Override
  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    return detail(delegate.details(portfolioId, id, date));
  }

  @Override
  public AssetView create(AssetCommand command) {
    return asset(delegate.create(toInternal(command)));
  }

  @Override
  public AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom) {
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
  public AssetView createBond(BondCommand command) {
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
                command.interestTreatment(),
                command.annualRatePercent(),
                command.notes())));
  }

  @Override
  public void update(AssetCommand command) {
    delegate.update(toInternal(command));
  }

  @Override
  public void updateBond(BondCommand command) {
    delegate.updateBond(
        new LongTermAssetsFacade.BondCommand(
            command.portfolioId(),
            command.id(),
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.maturityDate(),
            command.interestTreatment(),
            command.annualRatePercent(),
            command.notes()));
  }

  @Override
  public void saveRealEstate(Long portfolioId, RealEstateEntry entry) {
    delegate.saveRealEstate(portfolioId, entry);
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
  public void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date) {
    delegate.saveRentalPeriod(portfolioId, assetId, effectiveFrom, endDate, date);
  }

  @Override
  public void addCashFlow(CashFlowCommand command, LocalDate today) {
    delegate.addCashFlow(toInternal(command), today);
  }

  @Override
  public void changeCashFlow(CashFlowCommand command) {
    delegate.changeCashFlow(toInternal(command));
  }

  @Override
  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal percent, LocalDate from) {
    delegate.savePropertyGrowth(portfolioId, id, percent, from);
  }

  @Override
  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    delegate.saveBondDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondDetailsCommand(
            command.maturityDate(),
            command.taxRate(),
            command.interestTreatment(),
            command.redemptionValue()));
  }

  @Override
  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    delegate.saveDepositDetails(
        portfolioId,
        id,
        new LongTermAssetsFacade.DepositDetailsCommand(
            command.maturityDate(),
            command.annualInterestRate(),
            command.taxRate(),
            command.interestTreatment()));
  }

  @Override
  public void addValuation(Long portfolioId, Long id, ValuationCommand command) {
    delegate.addValuation(
        portfolioId,
        id,
        new LongTermAssetsFacade.ValuationCommand(
            command.validFrom(), command.validTo(), command.growthRatePercent()));
  }

  @Override
  public void addBondRate(Long portfolioId, Long id, BondRateCommand command) {
    delegate.addBondRate(
        portfolioId,
        id,
        new LongTermAssetsFacade.BondRateCommand(
            command.validFrom(), command.validTo(), command.annualInterestRate()));
  }

  @Override
  public void saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    delegate.saveRentalTaxPolicy(
        portfolioId,
        new LongTermAssetsFacade.RentalTaxCommand(
            command.validFrom(), command.validTo(), command.ratePercent(), command.rate()));
  }

  private static LongTermAssetsFacade.AssetCommand toInternal(AssetCommand c) {
    return new LongTermAssetsFacade.AssetCommand(
        c.portfolioId(),
        c.id(),
        c.name(),
        c.type(),
        c.currency(),
        c.acquisitionDate(),
        c.acquisitionValue(),
        c.currentValue(),
        c.taxBase(),
        c.active(),
        c.notes(),
        c.rentalTaxPaidByTenant());
  }

  private static LongTermAssetsFacade.CashFlowCommand toInternal(CashFlowCommand c) {
    return new LongTermAssetsFacade.CashFlowCommand(
        c.portfolioId(),
        c.assetId(),
        c.flowId(),
        c.type(),
        c.amount(),
        c.frequency(),
        c.validFrom(),
        c.validTo(),
        c.paidByTenant());
  }

  private static AssetView asset(LongTermAssetsFacade.AssetView v) {
    return new AssetView(
        v.id(),
        v.portfolioId(),
        v.name(),
        v.type(),
        v.currency(),
        v.acquisitionDate(),
        v.acquisitionValue(),
        v.currentValue(),
        v.taxBase(),
        v.active(),
        v.notes(),
        v.rentalTaxPaidByTenant());
  }

  private static DetailView detail(LongTermAssetsFacade.DetailView v) {
    return new DetailView(
        asset(v.asset()),
        summary(v.summary()),
        v.cashFlows().stream().map(LongTermAssetsApplicationService::flow).toList(),
        v.bondDetails() == null ? null : bond(v.bondDetails()),
        v.depositDetails() == null ? null : deposit(v.depositDetails()),
        v.valuationPeriods().stream().map(LongTermAssetsApplicationService::valuation).toList(),
        v.bondRatePeriods().stream().map(LongTermAssetsApplicationService::bondRate).toList(),
        v.currentCashFlows().stream().map(LongTermAssetsApplicationService::flow).toList(),
        new RentalPeriodView(v.rentalPeriod().effectiveFrom(), v.rentalPeriod().endDate()),
        v.availableCashFlowTypes(),
        v.expectedPropertyGrowth(),
        v.contracts().stream().map(c -> new RentalContractView(c.id(), c.startDate(), c.endDate(), c.terminatedDate(),
            c.terms().stream().map(t -> new RentalTermView(t.type(), t.amount(), t.frequency(), t.paidByTenant())).toList())).toList());
  }

  private static AssetSummaryView summary(LongTermAssetSummary s) {
    return new AssetSummaryView(
        s.id(),
        s.name(),
        s.type(),
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
        p.totalPaymentMonthly(),
        p.monthlyIncome(),
        p.monthlyReduce(),
        p.annualTax(),
        p.netMonthlyIncome());
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
        p.interestTreatment());
  }

  private static FlowView flow(LongTermAssetsFacade.FlowView f) {
    return new FlowView(
        f.id(),
        f.assetId(),
        f.type(),
        f.amount(),
        f.frequency(),
        f.validFrom(),
        f.validTo(),
        f.paidByTenant());
  }

  private static BondDetailsView bond(LongTermAssetsFacade.BondDetailsView d) {
    return new BondDetailsView(
        d.maturityDate(), d.taxRate(), d.interestTreatment(), d.redemptionValue());
  }

  private static DepositDetailsView deposit(LongTermAssetsFacade.DepositDetailsView d) {
    return new DepositDetailsView(
        d.maturityDate(), d.annualInterestRate(), d.taxRate(), d.interestTreatment());
  }

  private static ValuationView valuation(LongTermAssetsFacade.ValuationView p) {
    return new ValuationView(p.validFrom(), p.validTo(), p.expectedAnnualGrowthRate());
  }

  private static BondRateView bondRate(LongTermAssetsFacade.BondRateView p) {
    return new BondRateView(p.validFrom(), p.validTo(), p.annualInterestRate());
  }
}
