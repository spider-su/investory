package com.smartbox.investory.profile.api.model;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectedLongTermAsset(
    Long id,
    String name,
    EconomicBucket bucket,
    CurrencyType currency,
    BigDecimal currentValue,
    Liquidity liquidity,
    List<Period> periods,
    List<RentalContractProjectionModel> rentalContracts,
    LocalDate maturityDate) {
  public ProjectedLongTermAsset {
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

  /** Returns the same asset facts with a rebased aggregate value. */
  public ProjectedLongTermAsset withCurrentValue(BigDecimal value) {
    return new ProjectedLongTermAsset(
        id, name, bucket, currency, value, liquidity, periods, rentalContracts, maturityDate);
  }
}
