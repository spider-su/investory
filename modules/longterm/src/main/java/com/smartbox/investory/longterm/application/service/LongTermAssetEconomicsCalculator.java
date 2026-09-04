package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Calculates type-specific planning facts and normalized annual economics. */
@Component
public class LongTermAssetEconomicsCalculator {
  private final RealEstatePlanningCalculator realEstate = new RealEstatePlanningCalculator();
  private final BondPlanningCalculator bond = new BondPlanningCalculator();

  public LongTermAssetSummary summary(
      LongTermAssetEntity asset, LocalDate date, LongTermAssetRelatedDataLoader.Data data) {
    BigDecimal gross = BigDecimal.ZERO;
    BigDecimal expenses = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    BigDecimal rate = BigDecimal.ZERO;
    LocalDate maturity = null;
    RealEstatePlanningSummary realEstatePlanning = null;
    BondPlanningSummary bondPlanning = null;
    LocalDate rentEnd = null;

    if (asset.getType() == LongTermAssetType.REAL_ESTATE) {
      List<RentalContractModel> contracts =
          data.contracts().getOrDefault(asset.getId(), List.of()).stream()
              .map(LongTermAssetEconomicsCalculator::rentalContractModel)
              .toList();
      rentEnd =
          contracts.stream()
              .filter(
                  c ->
                      !date.isBefore(c.startDate())
                          && (effectiveContractEnd(c) == null
                              || !date.isAfter(effectiveContractEnd(c))))
              .filter(c -> c.terms().stream().anyMatch(t -> t.type() == CashFlowType.RENT))
              .max(Comparator.comparing(RentalContractModel::startDate))
              .map(LongTermAssetEconomicsCalculator::effectiveContractEnd)
              .orElse(null);
      realEstatePlanning =
          realEstate.calculate(
              asset.getCurrentValue(),
              asset.getTaxBase(),
              asset.isRentalTaxPaidByTenant(),
              contracts,
              date,
              data.rentalTaxRate());
      gross = normalize(realEstatePlanning.monthlyIncome().multiply(BigDecimal.valueOf(12)));
      expenses =
          normalize(
              realEstatePlanning
                  .monthlyReduce()
                  .multiply(BigDecimal.valueOf(12))
                  .subtract(realEstatePlanning.annualTax()));
      tax = realEstatePlanning.annualTax();
    } else if (asset.getType() == LongTermAssetType.BOND) {
      LongTermAssetBondDetailsEntity details = data.bonds().get(asset.getId());
      if (details != null) maturity = details.getMaturityDate();
      rate = currentRate(data.bondRates().getOrDefault(asset.getId(), List.of()), date);
      bondPlanning =
          bond.calculate(
              asset.getCurrentValue(),
              rate,
              maturity,
              details == null ? null : details.getInterestTreatment(),
              details == null
                  ? FinancialPolicyDefaults.BOND_TAX_RATE
                  : LongTermAssetPolicyRules.bondTaxRate(details.getTaxRate()));
      gross = bondPlanning.grossInterest();
      tax = bondPlanning.annualTax();
    } else if (asset.getType() == LongTermAssetType.DEPOSIT) {
      LongTermAssetDepositDetailsEntity details = data.deposits().get(asset.getId());
      if (details != null) {
        maturity = details.getMaturityDate();
        rate = details.getAnnualInterestRate();
        gross = asset.getCurrentValue().multiply(rate);
        tax = gross.multiply(details.getTaxRate());
      }
    } else if (asset.getType() == LongTermAssetType.CASH_RESERVE) {
      rate =
          data.valuations().getOrDefault(asset.getId(), List.of()).stream()
              .filter(p -> LongTermAssetCalculator.applies(p.getValidFrom(), p.getValidTo(), date))
              .reduce((first, second) -> second)
              .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
              .orElse(BigDecimal.ZERO);
      gross = asset.getCurrentValue().multiply(rate);
    }
    return new LongTermAssetSummary(
        asset.getId(),
        asset.getName(),
        asset.getType(),
        asset.getCurrency(),
        asset.getCurrentValue(),
        maturity,
        rate,
        AnnualEconomics.of(asset.getCurrentValue(), gross, expenses, tax),
        realEstatePlanning,
        bondPlanning,
        rentEnd);
  }

  public List<LongTermAssetSummary> summaries(
      List<LongTermAssetEntity> rows, LocalDate date, LongTermAssetRelatedDataLoader.Data data) {
    return rows.stream().map(row -> summary(row, date, data)).toList();
  }

  private static BigDecimal currentRate(
      List<LongTermAssetBondRatePeriodEntity> rates, LocalDate date) {
    return rates.stream()
        .filter(p -> LongTermAssetCalculator.applies(p.getValidFrom(), p.getValidTo(), date))
        .findFirst()
        .map(LongTermAssetBondRatePeriodEntity::getAnnualInterestRate)
        .orElse(BigDecimal.ZERO);
  }

  private static BigDecimal normalize(BigDecimal value) {
    BigDecimal rounded = value.setScale(3, java.math.RoundingMode.HALF_UP);
    return rounded.stripTrailingZeros().scale() <= 0
        ? rounded.setScale(0, java.math.RoundingMode.UNNECESSARY)
        : rounded.stripTrailingZeros();
  }

  private static RentalContractModel rentalContractModel(LongTermAssetRentalContractEntity c) {
    return new RentalContractModel(
        c.getId(),
        c.getStartDate(),
        c.getEndDate(),
        c.getTerminatedDate(),
        c.getRentalTaxPaidByTenant(),
        c.getMonthlyTaxBase(),
        c.getTenantName(),
        c.getTenantEmail(),
        c.getTenantPhone(),
        c.getTerms().stream()
            .map(
                t ->
                    new RentalContractModel.Term(
                        t.getType(), t.getAmount(), t.getFrequency(), t.isPaidByTenant()))
            .toList());
  }

  static LocalDate effectiveContractEnd(RentalContractModel c) {
    if (c.endDate() == null) return c.terminatedDate();
    if (c.terminatedDate() == null) return c.endDate();
    return c.endDate().isBefore(c.terminatedDate()) ? c.endDate() : c.terminatedDate();
  }
}
