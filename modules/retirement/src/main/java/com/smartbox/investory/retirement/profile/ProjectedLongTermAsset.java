package com.smartbox.investory.retirement.profile;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectedLongTermAsset(
    Long id,
    String name,
    LongTermAssetType type,
    EconomicBucket bucket,
    CurrencyType currency,
    BigDecimal currentValue,
    Liquidity liquidity,
    List<Period> periods,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatment interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
  public ProjectedLongTermAsset(
      Long id,
      String name,
      LongTermAssetType type,
      EconomicBucket bucket,
      CurrencyType currency,
      BigDecimal currentValue,
      Liquidity liquidity,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatment interestTreatment,
      BigDecimal taxRate) {
    this(
        id,
        name,
        type,
        bucket,
        currency,
        currentValue,
        liquidity,
        periods,
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        null,
        false);
  }

  public ProjectedLongTermAsset {
    periods = periods == null ? List.of() : List.copyOf(periods);
  }

  public record Period(
      LocalDate validFrom,
      LocalDate validTo,
      BigDecimal annualIncome,
      BigDecimal annualExpense,
      BigDecimal annualReturnRate,
      CashFlowType cashFlowType,
      boolean paidByTenant) {
    public Period(
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal annualIncome,
        BigDecimal annualExpense,
        BigDecimal annualReturnRate) {
      this(validFrom, validTo, annualIncome, annualExpense, annualReturnRate, null, false);
    }

    public Period(
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal annualIncome,
        BigDecimal annualExpense,
        BigDecimal annualReturnRate,
        CashFlowType cashFlowType) {
      this(validFrom, validTo, annualIncome, annualExpense, annualReturnRate, cashFlowType, false);
    }
  }

  public ProjectedLongTermAsset(
      Long id,
      String name,
      LongTermAssetType type,
      EconomicBucket bucket,
      CurrencyType currency,
      BigDecimal currentValue,
      Liquidity liquidity,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatment interestTreatment,
      BigDecimal taxRate,
      BigDecimal taxBase) {
    this(
        id,
        name,
        type,
        bucket,
        currency,
        currentValue,
        liquidity,
        periods,
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        taxBase,
        false);
  }
}
