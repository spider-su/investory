package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
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
    List<com.smartbox.investory.longterm.api.model.RentalContractModel> rentalContracts,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatment interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
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
        List.of(),
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        null,
        false);
  }

  public LongTermAssetProjectionInput {
    periods = periods == null ? List.of() : List.copyOf(periods);
    rentalContracts = rentalContracts == null ? List.of() : List.copyOf(rentalContracts);
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
      BigDecimal taxRate,
      BigDecimal taxBase) {
    this(
        id,
        name,
        type,
        currency,
        currentValue,
        periods,
        List.of(),
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        taxBase,
        false);
  }
}
