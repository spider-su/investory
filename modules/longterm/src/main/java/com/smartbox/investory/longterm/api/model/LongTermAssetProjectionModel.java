package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
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
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    List<Period> periods,
    List<RentalContractProjectionModel> rentalContracts,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatment interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
  public LongTermAssetProjectionModel {
    periods = CollectionUtils.immutableListOrEmpty(periods);
    rentalContracts = CollectionUtils.immutableListOrEmpty(rentalContracts);
  }

  public record Period(
      LocalDate validFrom,
      LocalDate validTo,
      BigDecimal annualIncome,
      BigDecimal annualExpense,
      BigDecimal annualReturnRate,
      CashFlowType cashFlowType,
      boolean paidByTenant) {}
}
