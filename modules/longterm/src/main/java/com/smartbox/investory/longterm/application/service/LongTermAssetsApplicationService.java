package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the public Long-Term API and translates internal application read models. */
@Service
@Primary
@Slf4j
@Transactional(readOnly = true)
public class LongTermAssetsApplicationService extends LongTermAssetsFacade
    implements LongTermAssetsApi, LongTermAssetAnnualSnapshotReader {
  private final Clock clock;

  @Autowired
  public LongTermAssetsApplicationService(
      LongTermAssetQueryService queries,
      LongTermAssetAnnualSnapshotService snapshots,
      LongTermAssetCommandService commands,
      RentalContractService rentalContracts,
      Clock clock) {
    super(queries, snapshots, commands, rentalContracts);
    this.clock = clock;
  }

  LongTermAssetsApplicationService(LongTermAssetsFacade source, Clock clock) {
    super(source);
    this.clock = clock;
  }

  @Override
  public LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year) {
    return super.historicalAnnualSnapshot(portfolioId, year);
  }

  @Override
  public List<AssetSummaryView> list(Long portfolioId, LocalDate date) {
    return super.listSummaries(portfolioId, date).stream()
        .map(LongTermAssetsApplicationService::summary)
        .toList();
  }

  @Override
  public List<AssetGroupView> grouped(Long portfolioId, LocalDate date) {
    return super.groupSummaries(portfolioId, date).stream()
        .map(LongTermAssetsApplicationService::group)
        .toList();
  }

  @Override
  public AggregateView aggregate(Long portfolioId, LocalDate date) {
    return aggregate(super.aggregateSummary(portfolioId, date));
  }

  @Override
  public AssetView asset(Long portfolioId, Long id) {
    return super.asset(portfolioId, id);
  }

  @Override
  public RentalContractView rentalContract(Long portfolioId, Long assetId, Long contractId) {
    return rental(
        super.rentalContractEntity(portfolioId, assetId, contractId), LocalDate.now(clock));
  }

  @Override
  public ValuationView valuation(Long portfolioId, Long assetId, Long periodId) {
    var value = super.valuationData(portfolioId, assetId, periodId);
    return new ValuationView(
        value.id(), value.validFrom(), value.validTo(), value.expectedAnnualGrowthRate());
  }

  @Override
  public RentalTaxView rentalTaxPolicy(Long portfolioId, Long policyId) {
    return super.rentalTaxPolicyData(portfolioId, policyId);
  }

  @Override
  public DetailView details(Long portfolioId, Long id, LocalDate date) {
    return super.details(portfolioId, id, date);
  }

  @Override
  @Transactional
  public AssetView create(AssetCommand command) {
    return command("create asset", command.portfolioId(), () -> super.create(command));
  }

  @Override
  @Transactional
  public AssetView saveCashReserve(CashReserveCommand command, LocalDate effectiveFrom) {
    return command(
        "save cash reserve",
        command.portfolioId(),
        () -> super.saveCashReserve(command, effectiveFrom));
  }

  @Override
  @Transactional
  public AssetView createBond(BondCommand command) {
    return command(
        "create bond",
        command.portfolioId(),
        () -> {
          return super.createBond(command);
        });
  }

  @Override
  public PageSnapshot page(Long portfolioId, LocalDate date) {
    var page = super.pageData(portfolioId, date);
    return new PageSnapshot(
        page.assets().stream().map(LongTermAssetsApplicationService::summary).toList(),
        page.groups().stream().map(LongTermAssetsApplicationService::group).toList(),
        aggregate(page.aggregate()));
  }

  @Override
  public List<AssetSummaryView> archived(Long portfolioId, LocalDate date) {
    return super.archivedSummaries(portfolioId, date).stream()
        .map(LongTermAssetsApplicationService::summary)
        .toList();
  }

  @Override
  @Transactional
  public AssetView createDeposit(DepositCommand command) {
    return command(
        "create deposit",
        command.portfolioId(),
        () -> {
          return super.createDeposit(command);
        });
  }

  @Override
  @Transactional
  public AssetView update(AssetCommand command) {
    return command("update asset", command.portfolioId(), () -> super.update(command));
  }

  @Override
  @Transactional
  public AssetView patch(AssetPatchCommand command) {
    return command("patch asset", command.portfolioId(), () -> super.patch(command));
  }

  @Override
  @Transactional
  public void updateBond(BondCommand command) {
    runCommand("update bond", command.portfolioId(), () -> super.updateBond(command));
  }

  @Override
  @Transactional
  public AssetView saveRealEstate(Long portfolioId, RealEstateEntryModel entry) {
    return command("save real estate", portfolioId, () -> super.saveRealEstate(portfolioId, entry));
  }

  @Override
  @Transactional
  public AssetView updateRealEstate(Long portfolioId, Long id, RealEstateEntryModel entry) {
    return command(
        "update real estate", portfolioId, () -> super.updateRealEstate(portfolioId, id, entry));
  }

  @Override
  @Transactional
  public RentalContractView createRentalContract(RentalContractCommand command) {
    return command(
        "create rental contract",
        command.portfolioId(),
        () -> rental(super.createRentalContractEntity(command), LocalDate.now(clock)));
  }

  @Override
  @Transactional
  public RentalContractView updateRentalContract(UpdateRentalContractCommand command) {
    return command(
        "update rental contract",
        command.portfolioId(),
        () -> rental(super.updateRentalContractEntity(command), LocalDate.now(clock)));
  }

  @Override
  @Transactional
  public void deleteRentalContract(Long portfolioId, Long assetId, Long contractId) {
    runCommand(
        "delete rental contract",
        portfolioId,
        () -> super.deleteRentalContract(portfolioId, assetId, contractId));
  }

  @Override
  @Transactional
  public RentalContractView endRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate endDate) {
    return command(
        "end rental contract",
        portfolioId,
        () ->
            rental(
                super.endRentalContractEntity(portfolioId, assetId, contractId, endDate),
                LocalDate.now(clock)));
  }

  @Override
  @Transactional
  public void terminateRentalContract(
      Long portfolioId, Long assetId, Long contractId, LocalDate terminationDate) {
    runCommand(
        "terminate rental contract",
        portfolioId,
        () -> super.terminateRentalContract(portfolioId, assetId, contractId, terminationDate));
  }

  @Override
  @Transactional
  public void saveTaxBase(Long portfolioId, Long id, BigDecimal value) {
    runCommand("save tax base", portfolioId, () -> super.saveTaxBase(portfolioId, id, value));
  }

  @Override
  @Transactional
  public void saveRentalTaxOwnership(Long portfolioId, Long id, boolean paidByTenant) {
    runCommand(
        "save rental tax ownership",
        portfolioId,
        () -> super.saveRentalTaxOwnership(portfolioId, id, paidByTenant));
  }

  @Override
  @Transactional
  public void archive(Long portfolioId, Long id) {
    runCommand("archive asset", portfolioId, () -> super.archive(portfolioId, id));
  }

  @Override
  @Transactional
  public void reactivate(Long portfolioId, Long id) {
    runCommand("reactivate asset", portfolioId, () -> super.reactivate(portfolioId, id));
  }

  @Override
  @Transactional
  public void savePropertyGrowth(Long portfolioId, Long id, BigDecimal rate, LocalDate from) {
    runCommand(
        "save property growth",
        portfolioId,
        () -> super.savePropertyGrowth(portfolioId, id, rate, from));
  }

  @Override
  @Transactional
  public void saveBondDetails(Long portfolioId, Long id, BondDetailsCommand command) {
    runCommand(
        "save bond details", portfolioId, () -> super.saveBondDetails(portfolioId, id, command));
  }

  @Override
  @Transactional
  public void saveDepositDetails(Long portfolioId, Long id, DepositDetailsCommand command) {
    runCommand(
        "save deposit details",
        portfolioId,
        () -> super.saveDepositDetails(portfolioId, id, command));
  }

  @Override
  @Transactional
  public ValuationView addValuation(Long portfolioId, Long id, ValuationCommand command) {
    return command(
        "add valuation", portfolioId, () -> super.addValuation(portfolioId, id, command));
  }

  @Override
  @Transactional
  public void updateValuation(Long portfolioId, Long id, Long periodId, ValuationCommand command) {
    runCommand(
        "update valuation",
        portfolioId,
        () -> super.updateValuation(portfolioId, id, periodId, command));
  }

  @Override
  @Transactional
  public void deleteValuation(Long portfolioId, Long id, Long periodId) {
    runCommand(
        "delete valuation", portfolioId, () -> super.deleteValuation(portfolioId, id, periodId));
  }

  @Override
  @Transactional
  public RentalTaxView saveRentalTaxPolicy(Long portfolioId, RentalTaxCommand command) {
    return command(
        "save rental tax policy",
        portfolioId,
        () -> super.saveRentalTaxPolicy(portfolioId, command));
  }

  @Override
  @Transactional
  public void updateRentalTaxPolicy(Long portfolioId, Long policyId, RentalTaxCommand command) {
    runCommand(
        "update rental tax policy",
        portfolioId,
        () -> super.saveRentalTaxPolicy(portfolioId, policyId, command));
  }

  @Override
  @Transactional
  public void deleteRentalTaxPolicy(Long portfolioId, Long policyId) {
    runCommand(
        "delete rental tax policy",
        portfolioId,
        () -> super.deleteRentalTaxPolicy(portfolioId, policyId));
  }

  private <T> T command(String operation, Long portfolioId, java.util.function.Supplier<T> action) {
    try {
      T value = action.get();
      log.info(
          "Long-term asset operation succeeded: operation={} portfolioId={}",
          operation,
          portfolioId);
      return value;
    } catch (RuntimeException exception) {
      if (exception instanceof IllegalArgumentException
          || exception instanceof ResourceNotFoundException) {
        log.debug(
            "Long-term asset operation rejected: operation={} portfolioId={} message={}",
            operation,
            portfolioId,
            exception.getMessage());
        throw exception;
      }
      log.error(
          "Long-term asset operation failed: operation={} portfolioId={}",
          operation,
          portfolioId,
          exception);
      throw exception;
    }
  }

  private void runCommand(String operation, Long portfolioId, Runnable action) {
    command(
        operation,
        portfolioId,
        () -> {
          action.run();
          return null;
        });
  }

  @Override
  public List<RentalTaxView> rentalTaxPolicies(Long portfolioId) {
    return super.rentalTaxPolicies(portfolioId);
  }

  private static RentalContractView rental(LongTermAssetRentalContractEntity c, LocalDate date) {
    return new RentalContractView(
        c.getId(),
        c.getTenantName(),
        c.getTenantEmail(),
        c.getTenantPhone(),
        c.getStartDate(),
        c.getEndDate(),
        c.getTerminatedDate(),
        RentalContractService.effectiveEnd(c),
        RentalContractService.status(c, date),
        c.getRentalTaxPaidByTenant(),
        c.getMonthlyTaxBase(),
        c.getTerms().stream()
            .map(
                t ->
                    new RentalTermView(
                        t.getType(), t.getAmount(), t.getFrequency(), t.isPaidByTenant()))
            .toList());
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

  private static AssetGroupView group(LongTermAssetQueryService.AssetGroupSummary g) {
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
                g.realEstatePlanning().monthlyReduce(),
                g.realEstatePlanning().taxBase(),
                g.realEstatePlanning().monthlyRentTax(),
                g.realEstatePlanning().incomeYield()));
  }

  private static AggregateView aggregate(LongTermAssetQueryService.AggregateSummary a) {
    return new AggregateView(a.currency(), a.totalCurrentValue(), economics(a.annualEconomics()));
  }

  private static AnnualEconomicsView economics(AnnualEconomics e) {
    return new AnnualEconomicsView(
        e.grossAnnualIncome(),
        e.annualExpenses(),
        e.annualTax(),
        e.netAnnualIncomeBeforeTax(),
        e.netAnnualIncomeAfterTax(),
        e.monthlyNetIncomeAfterTax(),
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
        p.interestTreatment());
  }
}
