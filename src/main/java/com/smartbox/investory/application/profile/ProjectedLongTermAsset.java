package com.smartbox.investory.application.profile;

import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
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
    BigDecimal taxBase) {
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
        null);
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
      CashFlowType cashFlowType) {
    public Period(
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal annualIncome,
        BigDecimal annualExpense,
        BigDecimal annualReturnRate) {
      this(validFrom, validTo, annualIncome, annualExpense, annualReturnRate, null);
    }
  }
}
