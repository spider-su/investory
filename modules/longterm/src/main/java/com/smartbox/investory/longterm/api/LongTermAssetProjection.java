package com.smartbox.investory.longterm.api;

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
public record LongTermAssetProjection(
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
  public LongTermAssetProjection(
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

  public LongTermAssetProjection {
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
