package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LongTermAssetService {
  public static final BigDecimal REAL_ESTATE_TAX_RATE =
      LongTermAssetQueryService.REAL_ESTATE_TAX_RATE;
  public static final BigDecimal BOND_TAX_RATE = LongTermAssetCommandService.BOND_TAX_RATE;
  private final LongTermAssetQueryService queries;
  private final LongTermAssetProjectionQueryService projections;
  private final LongTermAssetAnnualSnapshotService snapshots;
  private final LongTermAssetCommandService commands;

  public LongTermAssetService(
      LongTermAssetQueryService queries,
      LongTermAssetProjectionQueryService projections,
      LongTermAssetAnnualSnapshotService snapshots,
      LongTermAssetCommandService commands) {
    this.queries = queries;
    this.projections = projections;
    this.snapshots = snapshots;
    this.commands = commands;
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    return queries.list(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> archived(Long portfolioId, LocalDate date) {
    return queries.archived(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetQueryService.AssetGroupSummary> grouped(
      Long portfolioId, LocalDate date) {
    return queries.grouped(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.PageData page(Long portfolioId, LocalDate date) {
    return queries.page(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetQueryService.AssetGroupSummary> groupSummaries(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
    return queries.groupSummaries(rows, base, date);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetEntity> get(Long portfolioId, Long id) {
    return queries.get(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> cashFlows(Long portfolioId, Long id) {
    return queries.cashFlows(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> currentCashFlows(
      Long portfolioId, Long id, LocalDate date) {
    return queries.currentCashFlows(portfolioId, id, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.RentalPeriod rentalPeriod(
      Long portfolioId, Long id, LocalDate date) {
    return queries.rentalPeriod(portfolioId, id, date);
  }

  @Transactional(readOnly = true)
  public List<CashFlowType> availableCashFlowTypes(Long portfolioId, Long id, LocalDate date) {
    return queries.availableCashFlowTypes(portfolioId, id, date);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetBondDetailsEntity> bondDetails(Long portfolioId, Long id) {
    return queries.bondDetails(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetDepositDetailsEntity> depositDetails(Long portfolioId, Long id) {
    return queries.depositDetails(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetValuationPeriodEntity> valuationPeriods(Long portfolioId, Long id) {
    return queries.valuationPeriods(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public BigDecimal expectedPropertyGrowth(Long portfolioId, Long id, LocalDate date) {
    return queries.expectedPropertyGrowth(portfolioId, id, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetBondRatePeriodEntity> bondRatePeriods(Long portfolioId, Long id) {
    return queries.bondRatePeriods(portfolioId, id);
  }

  @Transactional(readOnly = true)
  public Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    return queries.rentalTaxPolicy(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetProjectionInput> projectionInputs(Long portfolioId, LocalDate date) {
    return projections.projectionInputs(portfolioId, date);
  }

  public RentalTaxPolicyEntity saveRentalTaxPolicy(Long portfolioId, RentalTaxPolicyEntity policy) {
    return commands.saveRentalTaxPolicy(portfolioId, policy);
  }

  public LongTermAssetEntity updateTaxBase(Long portfolioId, Long assetId, BigDecimal taxBase) {
    return commands.updateTaxBase(portfolioId, assetId, taxBase);
  }

  public LongTermAssetEntity save(LongTermAssetEntity asset) {
    return commands.save(asset);
  }

  public LongTermAssetEntity saveCashReserve(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnRate,
      String notes,
      LocalDate effectiveFrom) {
    return commands.saveCashReserve(
        portfolioId, id, name, currency, value, annualReturnRate, notes, effectiveFrom);
  }

  public LongTermAssetEntity saveRealEstateEntry(
      Long portfolioId, Long id, RealEstateEntryModel entry) {
    return commands.saveRealEstateEntry(portfolioId, id, entry);
  }

  public LongTermAssetBondDetailsEntity saveBondDetails(
      Long portfolioId, Long assetId, LongTermAssetBondDetailsEntity details) {
    return commands.saveBondDetails(portfolioId, assetId, details);
  }

  public void saveSimpleBond(
      Long portfolioId,
      Long assetId,
      BigDecimal rate,
      LocalDate maturityDate,
      InterestTreatment treatment) {
    commands.saveSimpleBond(portfolioId, assetId, rate, maturityDate, treatment);
  }

  public LongTermAssetDepositDetailsEntity saveDepositDetails(
      Long portfolioId, Long assetId, LongTermAssetDepositDetailsEntity details) {
    return commands.saveDepositDetails(portfolioId, assetId, details);
  }

  public void archive(Long portfolioId, Long id) {
    commands.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    commands.reactivate(portfolioId, id);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow) {
    return commands.addCashFlow(portfolioId, assetId, flow);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow, LocalDate date) {
    return commands.addCashFlow(portfolioId, assetId, flow, date);
  }

  public void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date) {
    commands.saveRentalPeriod(portfolioId, assetId, effectiveFrom, endDate, date);
  }

  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom,
      LocalDate validTo) {
    return commands.changeCashFlow(
        portfolioId, assetId, flowId, amount, frequency, effectiveFrom, validTo);
  }

  public LongTermAssetCashFlowEntity changeCurrentCashFlow(
      Long portfolioId, Long assetId, Long flowId, BigDecimal amount, Frequency frequency) {
    return commands.changeCurrentCashFlow(portfolioId, assetId, flowId, amount, frequency);
  }

  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom) {
    return commands.changeCashFlow(portfolioId, assetId, flowId, amount, frequency, effectiveFrom);
  }

  public void saveExpectedPropertyGrowth(
      Long portfolioId, Long assetId, BigDecimal rate, LocalDate from) {
    commands.saveExpectedPropertyGrowth(portfolioId, assetId, rate, from);
  }

  public void deleteCashFlow(Long portfolioId, Long assetId, Long flowId) {
    commands.deleteCashFlow(portfolioId, assetId, flowId);
  }

  public void setCashFlowPaidByTenant(
      Long portfolioId, Long assetId, Long flowId, boolean paidByTenant) {
    commands.setCashFlowPaidByTenant(portfolioId, assetId, flowId, paidByTenant);
  }

  public LongTermAssetValuationPeriodEntity addValuationPeriod(
      Long portfolioId, Long assetId, LongTermAssetValuationPeriodEntity period) {
    return commands.addValuationPeriod(portfolioId, assetId, period);
  }

  public LongTermAssetBondRatePeriodEntity addBondRatePeriod(
      Long portfolioId, Long assetId, LongTermAssetBondRatePeriodEntity period) {
    return commands.addBondRatePeriod(portfolioId, assetId, period);
  }

  @Transactional(readOnly = true)
  public LongTermAssetSummary summary(LongTermAssetEntity asset, LocalDate date) {
    return queries.summary(asset, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.AggregateSummary aggregate(Long portfolioId, LocalDate date) {
    return queries.aggregate(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetQueryService.AggregateSummary aggregateForLongTermAssets(
      Long portfolioId, LocalDate date) {
    return queries.aggregateForLongTermAssets(portfolioId, date);
  }

  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year) {
    return snapshots.historicalAnnualSnapshot(portfolioId, year);
  }

  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel currentAnnualSnapshot(Long portfolioId, LocalDate date) {
    return snapshots.currentAnnualSnapshot(portfolioId, date);
  }
}
