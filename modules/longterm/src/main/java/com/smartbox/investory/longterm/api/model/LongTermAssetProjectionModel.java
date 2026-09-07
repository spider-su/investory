package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistence-free Long-Term projection input for retirement consumers.
 *
 * <p>All monetary fields, including period amounts, are expressed in the portfolio local currency
 * (from {@code portfolios.local_currency}). The {@link #currency} field reports that local
 * currency; profile composition converts it to the investment base currency. {@code
 * fundingAvailable} is the Long-Term-owned current funding decision; downstream modules must not
 * reconstruct it from maturity or asset type.
 */
public record LongTermAssetProjectionModel(
    Long id,
    String name,
    AssetEconomicCategory category,
    CurrencyType currency,
    BigDecimal currentValue,
    List<Period> periods,
    List<RentalContractProjectionModel> rentalContracts,
    LocalDate maturityDate,
    boolean fundingAvailable) {
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
