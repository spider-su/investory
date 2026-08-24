package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetProjectionQueryService {
  public static final BigDecimal REAL_ESTATE_TAX_RATE = new BigDecimal("0.085");
  public static final BigDecimal BOND_TAX_RATE = new BigDecimal("0.19");
  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final RentalTaxPolicyRepository taxPolicies;
  private final LongTermAssetRentalContractRepository rentalContracts;
  private final LongTermAssetLifecycleService lifecycle;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  public LongTermAssetProjectionQueryService(
      LongTermAssetRepository assets,
      LongTermAssetCashFlowRepository cashFlows,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      RentalTaxPolicyRepository taxPolicies,
      LongTermAssetRentalContractRepository rentalContracts,
      LongTermAssetLifecycleService lifecycle) {
    this.assets = assets;
    this.cashFlows = cashFlows;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.taxPolicies = taxPolicies;
    this.rentalContracts = rentalContracts;
    this.lifecycle = lifecycle;
  }

  /** Builds native-domain projection data; the public reader normalizes its money to USD. */
  @Transactional(readOnly = true)
  public List<LongTermAssetProjectionInput> projectionInputs(
      Long portfolioId, LocalDate policyDate) {
    return projectionInputsAt(portfolioId, policyDate, true);
  }

  private List<LongTermAssetProjectionInput> projectionInputsAt(
      Long portfolioId, LocalDate policyDate, boolean activeOnly) {
    LocalDate effectivePolicyDate = effectiveDate(policyDate);
    List<LongTermAssetProjectionInput> result = new ArrayList<>();
    for (LongTermAssetEntity asset : assets.findAllByPortfolioIdOrderByName(portfolioId)) {
      if (activeOnly && !asset.isActive()) continue;
      if (!activeOnly && !activeOn(asset, effectivePolicyDate)) continue;
      List<LongTermAssetProjectionInput.Period> periods = new ArrayList<>();
      List<RentalContractModel> contracts =
          rentalContracts.findAllByAssetIdOrderByStartDate(asset.getId()).stream()
              .map(
                  c ->
                      new RentalContractModel(
                          c.getId(),
                          c.getStartDate(),
                          c.getEndDate(),
                          c.getTerminatedDate(),
                          c.getRentalTaxPaidByTenant(),
                          c.getTerms().stream()
                              .map(
                                  t ->
                                      new RentalContractModel.Term(
                                          com.smartbox.investory.longterm.api.model
                                              .CashFlowTypeModel.valueOf(t.getType().name()),
                                          t.getAmount(),
                                          com.smartbox.investory.longterm.api.model.FrequencyModel
                                              .valueOf(t.getFrequency().name()),
                                          t.isPaidByTenant()))
                              .toList()))
              .toList();
      if (asset.getType() != LongTermAssetType.REAL_ESTATE) {
        for (LongTermAssetCashFlowEntity flow :
            cashFlows.findAllByAssetIdOrderByValidFrom(asset.getId())) {
          BigDecimal annual = LongTermAssetCalculator.annualAmount(flow);
          periods.add(
              new LongTermAssetProjectionInput.Period(
                  flow.getValidFrom(),
                  flow.getValidTo(),
                  isIncome(flow.getType()) ? annual : BigDecimal.ZERO,
                  isIncome(flow.getType()) ? BigDecimal.ZERO : annual,
                  BigDecimal.ZERO,
                  flow.getType(),
                  flow.isPaidByTenant()));
        }
      }
      for (LongTermAssetValuationPeriodEntity period :
          valuations.findAllByAssetIdOrderByValidFrom(asset.getId()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getExpectedAnnualGrowthRate()));
      for (LongTermAssetBondRatePeriodEntity period :
          bondRates.findAllByAssetIdOrderByValidFrom(asset.getId()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getAnnualInterestRate()));
      LocalDate maturity = null;
      BigDecimal redemption = null, tax = BigDecimal.ZERO, taxBase = null;
      InterestTreatment treatment = null;
      if (asset.getType() == LongTermAssetType.REAL_ESTATE) {
        tax =
            rentalTaxPolicy(portfolioId, effectivePolicyDate)
                .map(RentalTaxPolicyEntity::getRate)
                .orElse(REAL_ESTATE_TAX_RATE);
        taxBase = asset.getTaxBase();
      } else if (asset.getType() == LongTermAssetType.BOND) {
        LongTermAssetBondDetailsEntity d = bonds.findById(asset.getId()).orElse(null);
        if (d != null) {
          maturity = d.getMaturityDate();
          redemption = d.getRedemptionValue();
          treatment = d.getInterestTreatment();
          tax = d.getTaxRate() == null ? BOND_TAX_RATE : d.getTaxRate();
        }
      } else if (asset.getType() == LongTermAssetType.DEPOSIT) {
        LongTermAssetDepositDetailsEntity d = deposits.findById(asset.getId()).orElse(null);
        if (d != null) {
          maturity = d.getMaturityDate();
          tax = d.getTaxRate();
          treatment = d.getInterestTreatment();
          periods.add(
              new LongTermAssetProjectionInput.Period(
                  asset.getAcquisitionDate() == null
                      ? effectivePolicyDate
                      : asset.getAcquisitionDate(),
                  maturity,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  d.getAnnualInterestRate()));
        }
      }
      result.add(
          new LongTermAssetProjectionInput(
              asset.getId(),
              asset.getName(),
              asset.getType(),
              asset.getCurrency(),
              asset.getCurrentValue(),
              periods,
              contracts,
              maturity,
              redemption,
              treatment,
              tax,
              taxBase,
              asset.isRentalTaxPaidByTenant()));
    }
    return result;
  }

  private Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    return taxPolicies.findAllByPortfolioIdOrderByValidFrom(portfolioId).stream()
        .filter(
            policy ->
                LongTermAssetCalculator.applies(policy.getValidFrom(), policy.getValidTo(), date))
        .findFirst();
  }

  private LocalDate effectiveDate(LocalDate date) {
    return date != null && date.isBefore(historyStart) ? historyStart : date;
  }

  private boolean activeOn(LongTermAssetEntity asset, LocalDate date) {
    return lifecycle.activeOn(asset, date);
  }

  private static boolean isIncome(CashFlowType type) {
    return type == CashFlowType.RENT
        || type == CashFlowType.PARKING_RENT
        || type == CashFlowType.OTHER_INCOME;
  }
}
