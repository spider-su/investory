package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetProjectionQueryService {
  private final LongTermAssetPopulationLoader population;

  @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
  private LocalDate historyStart = FinancialPolicyDefaults.HISTORY_START;

  public LongTermAssetProjectionQueryService(LongTermAssetPopulationLoader population) {
    this.population = population;
  }

  /** Builds native-domain projection data; the public reader normalizes its money to USD. */
  @Transactional(readOnly = true)
  public List<LongTermAssetProjectionInput> projectionInputs(
      Long portfolioId, LocalDate policyDate) {
    return snapshot(portfolioId, policyDate).inputs();
  }

  Snapshot snapshot(Long portfolioId, LocalDate policyDate) {
    LocalDate effectivePolicyDate = effectiveDate(policyDate);
    List<LongTermAssetEntity> assetRows = population.activeAt(portfolioId, effectivePolicyDate);
    if (assetRows.isEmpty())
      return new Snapshot(assetRows, LongTermAssetRelatedDataLoader.Data.empty(), List.of());
    LongTermAssetRelatedDataLoader.Data data =
        population.relatedData(portfolioId, assetRows, effectivePolicyDate);
    return new Snapshot(assetRows, data, projectionInputsAt(assetRows, data, effectivePolicyDate));
  }

  record Snapshot(
      List<LongTermAssetEntity> assets,
      LongTermAssetRelatedDataLoader.Data data,
      List<LongTermAssetProjectionInput> inputs) {}

  private List<LongTermAssetProjectionInput> projectionInputsAt(
      List<LongTermAssetEntity> assetRows,
      LongTermAssetRelatedDataLoader.Data data,
      LocalDate effectivePolicyDate) {
    List<LongTermAssetProjectionInput> result = new ArrayList<>();
    for (LongTermAssetEntity asset : assetRows) {
      if (!asset.getType().contributesToCalculations()) continue;
      List<LongTermAssetProjectionInput.Period> periods = new ArrayList<>();
      List<RentalContractProjectionModel> contracts =
          data.contracts().getOrDefault(asset.getId(), List.of()).stream()
              .map(
                  c ->
                      new RentalContractProjectionModel(
                          c.getId(),
                          c.getStartDate(),
                          c.getEndDate(),
                          c.getTerminatedDate(),
                          c.getRentalTaxPaidByTenant(),
                          c.getMonthlyTaxBase(),
                          c.getTerms().stream()
                              .map(
                                  t ->
                                      new RentalContractModel.Term(
                                          t.getType(),
                                          t.getAmount(),
                                          t.getFrequency(),
                                          t.isPaidByTenant()))
                              .toList()))
              .toList();
      for (LongTermAssetValuationPeriodEntity period :
          data.valuations().getOrDefault(asset.getId(), List.of()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getExpectedAnnualGrowthRate(),
                null,
                false));
      for (LongTermAssetBondRatePeriodEntity period :
          data.bondRates().getOrDefault(asset.getId(), List.of()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getAnnualInterestRate(),
                null,
                false));
      LocalDate maturity = null;
      BigDecimal redemption = null, tax = BigDecimal.ZERO, taxBase = null;
      InterestTreatment treatment = null;
      if (asset.getType() == LongTermAssetType.REAL_ESTATE) {
        tax = data.rentalTaxRate();
        taxBase = asset.getTaxBase();
      } else if (asset.getType() == LongTermAssetType.BOND) {
        LongTermAssetBondDetailsEntity d = data.bonds().get(asset.getId());
        if (d != null) {
          maturity = d.getMaturityDate();
          redemption = d.getRedemptionValue();
          treatment = d.getInterestTreatment();
          tax = LongTermAssetPolicyRules.bondTaxRate(d.getTaxRate());
        }
      } else if (asset.getType() == LongTermAssetType.DEPOSIT) {
        LongTermAssetDepositDetailsEntity d = data.deposits().get(asset.getId());
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
                  d.getAnnualInterestRate(),
                  null,
                  false));
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

  private LocalDate effectiveDate(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("date must not be null");
    return date.isBefore(historyStart) ? historyStart : date;
  }
}
