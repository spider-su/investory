package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LongTermAssetBootstrapDocument(
    Long portfolioId, List<TaxPolicy> rentalTaxPolicies, List<AssetEntity> assets) {
  public record TaxPolicy(LocalDate validFrom, LocalDate validTo, BigDecimal rate) {}

  public record AssetEntity(
      String externalKey,
      LongTermAssetType type,
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      LocalDate effectiveFrom,
      String notes,
      List<CashFlow> cashFlows,
      List<Period> valuationPeriods,
      List<Period> bondRatePeriods,
      Bond bond,
      Deposit deposit,
      BigDecimal taxBase,
      Boolean rentalTaxPaidByTenant) {
    public AssetEntity(
        String externalKey,
        LongTermAssetType type,
        String name,
        CurrencyType currency,
        LocalDate acquisitionDate,
        BigDecimal acquisitionValue,
        BigDecimal currentValue,
        LocalDate effectiveFrom,
        String notes,
        List<CashFlow> cashFlows,
        List<Period> valuationPeriods,
        List<Period> bondRatePeriods,
        Bond bond,
        Deposit deposit) {
      this(
          externalKey,
          type,
          name,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          effectiveFrom,
          notes,
          cashFlows,
          valuationPeriods,
          bondRatePeriods,
          bond,
          deposit,
          null,
          null);
    }
  }

  public record CashFlow(
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo,
      Boolean paidByTenant) {
    public CashFlow(
        CashFlowType type,
        BigDecimal amount,
        Frequency frequency,
        LocalDate validFrom,
        LocalDate validTo) {
      this(type, amount, frequency, validFrom, validTo, null);
    }
  }

  public record Period(LocalDate validFrom, LocalDate validTo, BigDecimal annualRate) {}

  public record Bond(
      LocalDate maturityDate,
      InterestTreatment interestTreatment,
      BigDecimal taxRate,
      BigDecimal redemptionValue) {}

  public record Deposit(
      LocalDate maturityDate,
      InterestTreatment interestTreatment,
      BigDecimal annualInterestRate,
      BigDecimal taxRate) {}
}
