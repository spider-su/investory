package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.InterestTreatment;
import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LongTermAssetProjectionInput(
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    List<Period> periods,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatment interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase) {
  public LongTermAssetProjectionInput(
      Long id,
      String name,
      LongTermAssetType type,
      CurrencyType currency,
      BigDecimal currentValue,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatment interestTreatment,
      BigDecimal taxRate) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        periods,
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        null);
  }

  public LongTermAssetProjectionInput {
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
