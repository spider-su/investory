package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Total return of one rental property in its immutable asset currency. */
public record RealEstateReturnView(
    BigDecimal acquisitionValue, BigDecimal rentalProfit, BigDecimal totalReturn) {
  public boolean available() {
    return totalReturn != null;
  }
}
