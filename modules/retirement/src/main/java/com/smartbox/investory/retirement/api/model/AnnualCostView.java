package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

public record AnnualCostView(
    boolean available, BigDecimal amount, CurrencyType currency, int year, Long planId) {
  public static AnnualCostView unavailable(CurrencyType currency, int year) {
    return new AnnualCostView(false, null, currency, year, null);
  }
}
