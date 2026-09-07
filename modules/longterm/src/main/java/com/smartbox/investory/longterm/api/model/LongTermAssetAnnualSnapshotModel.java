package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/**
 * Canonical annual long-term-asset facts shared by overview and historical planning.
 *
 * <p>The historical-reader contract returns values in the portfolio local currency. When nested in
 * a current profile snapshot, amounts use that snapshot's explicitly declared portfolio currency.
 */
public record LongTermAssetAnnualSnapshotModel(
    BigDecimal realEstateValue,
    BigDecimal rentalIncome,
    BigDecimal bondValue,
    BigDecimal bondIncome,
    BigDecimal cashReserveValue,
    BigDecimal otherAssetValue,
    com.smartbox.investory.shared.currency.CurrencyType currency) {
  public LongTermAssetAnnualSnapshotModel {
    java.util.Objects.requireNonNull(currency, "currency");
  }

  /** Compatibility constructor for callers that use the historical default currency. */
  public LongTermAssetAnnualSnapshotModel(
      BigDecimal realEstateValue,
      BigDecimal rentalIncome,
      BigDecimal bondValue,
      BigDecimal bondIncome,
      BigDecimal cashReserveValue,
      BigDecimal otherAssetValue) {
    this(
        realEstateValue,
        rentalIncome,
        bondValue,
        bondIncome,
        cashReserveValue,
        otherAssetValue,
        com.smartbox.investory.shared.currency.CurrencyType.USD);
  }

  public boolean rentalIncomeAvailable() {
    return rentalIncome != null;
  }

  public boolean bondValueAvailable() {
    return bondValue != null;
  }

  public boolean bondIncomeAvailable() {
    return bondIncome != null;
  }

  public boolean cashReserveValueAvailable() {
    return cashReserveValue != null;
  }

  public boolean realEstateValueAvailable() {
    return realEstateValue != null;
  }
}
