package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/**
 * Canonical annual long-term-asset facts shared by overview and historical planning.
 *
 * <p>Every monetary amount in this API contract is canonical USD. Native asset currency is an
 * internal persistence/domain concern and is normalized before this record is returned.
 */
public record LongTermAssetAnnualSnapshotModel(
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
