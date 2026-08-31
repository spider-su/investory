package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistence-free Long-Term projection input for retirement consumers.
 *
 * <p>All monetary fields, including period amounts, redemption value and tax base, are canonical
 * USD. The currency field is therefore always {@link CurrencyType#USD}; rates and dates are not
 * converted.
 */
public record LongTermAssetProjectionModel(
    Long id,
    String name,
    LongTermAssetTypeModel type,
    CurrencyType currency,
    BigDecimal currentValue,
    List<Period> periods,
    List<RentalContractModel> rentalContracts,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatmentModel interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
  public LongTermAssetProjectionModel(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      BigDecimal currentValue,
      List<Period> periods,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      InterestTreatmentModel interestTreatment,
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

  public LongTermAssetProjectionModel {
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

  public LongTermAssetProjectionModel(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      BigDecimal currentValue,
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

  public LongTermAssetProjectionModel(
      Long id,
      String name,
      LongTermAssetTypeModel type,
      CurrencyType currency,
      BigDecimal currentValue,
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
        currency,
        currentValue,
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
