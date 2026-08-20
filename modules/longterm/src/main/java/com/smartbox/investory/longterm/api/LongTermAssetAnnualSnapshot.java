package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;

/** Canonical annual long-term-asset facts shared by overview and historical planning. */
public record LongTermAssetAnnualSnapshot(
    BigDecimal realEstateValue,
    BigDecimal rentalIncome,
    BigDecimal bondValue,
    BigDecimal bondIncome,
    BigDecimal cashReserveValue,
    BigDecimal otherAssetValue) {
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
