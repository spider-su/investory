package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
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
    List<com.smartbox.investory.longterm.api.model.RentalContractProjectionModel> rentalContracts,
    LocalDate maturityDate,
    BigDecimal redemptionValue,
    InterestTreatment interestTreatment,
    BigDecimal taxRate,
    BigDecimal taxBase,
    boolean rentalTaxPaidByTenant) {
  public LongTermAssetProjectionInput {
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
