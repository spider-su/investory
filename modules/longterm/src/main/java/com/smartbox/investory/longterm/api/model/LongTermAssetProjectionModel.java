package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistence-free Long-Term projection input for retirement consumers.
 *
 * <p>All monetary fields, including period amounts, redemption value and tax base, are expressed in
 * the portfolio base currency (from {@code portfolios.base_currency}). The {@link #currency} field
 * reports that base currency; conversion happens once at read time and rates are not re-applied.
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
