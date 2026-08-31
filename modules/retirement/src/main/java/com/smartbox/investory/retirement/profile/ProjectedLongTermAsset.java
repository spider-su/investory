package com.smartbox.investory.retirement.profile;

import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectedLongTermAsset(
    Long id,
    String name,
    LongTermAssetTypeModel type,
    EconomicBucket bucket,
    CurrencyType currency,
    BigDecimal currentValue,
    Liquidity liquidity,
    List<Period> periods,
    List<RentalContractModel> rentalContracts,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatmentModel interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
  public ProjectedLongTermAsset(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      EconomicBucket bucket,
      CurrencyType currency,
      BigDecimal currentValue,
      Liquidity liquidity,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatmentModel interestTreatment,
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
        List.of(),
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        null,
        false);
  }

  public ProjectedLongTermAsset {
    periods = periods == null ? List.of() : List.copyOf(periods);
    rentalContracts = rentalContracts == null ? List.of() : List.copyOf(rentalContracts);
  }

  public record Period(
      LocalDate validFrom,
      LocalDate validTo,
      BigDecimal annualIncome,
      BigDecimal annualExpense,
      BigDecimal annualReturnRate,
      CashFlowTypeModel cashFlowType,
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
        CashFlowTypeModel cashFlowType) {
      this(validFrom, validTo, annualIncome, annualExpense, annualReturnRate, cashFlowType, false);
    }
  }

  public ProjectedLongTermAsset(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      EconomicBucket bucket,
      CurrencyType currency,
      BigDecimal currentValue,
      Liquidity liquidity,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatmentModel interestTreatment,
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
        List.of(),
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        taxBase,
        false);
  }

  public ProjectedLongTermAsset(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      EconomicBucket bucket,
      CurrencyType currency,
      BigDecimal currentValue,
      Liquidity liquidity,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatmentModel interestTreatment,
      BigDecimal taxRate,
      BigDecimal taxBase,
      boolean rentalTaxPaidByTenant) {
    this(
        id,
        name,
        type,
        bucket,
        currency,
        currentValue,
        liquidity,
        periods,
        List.of(),
        maturityDate,
        redemptionValue,
        interestTreatment,
        taxRate,
        taxBase,
        rentalTaxPaidByTenant);
  }
}
