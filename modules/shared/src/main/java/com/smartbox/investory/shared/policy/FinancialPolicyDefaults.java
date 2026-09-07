package com.smartbox.investory.shared.policy;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Canonical defaults shared by financial calculations and their adapters. */
public final class FinancialPolicyDefaults {
  public static final CurrencyType CANONICAL_CURRENCY = CurrencyType.USD;
  public static final String HISTORY_START_TEXT = "2025-01-01";
  public static final LocalDate HISTORY_START = LocalDate.parse(HISTORY_START_TEXT);
  public static final BigDecimal GLOBAL_PROFIT_TAX_RATE = new BigDecimal("0.19");

  /**
   * @deprecated use {@link #GLOBAL_PROFIT_TAX_RATE}.
   */
  @Deprecated(forRemoval = false)
  public static final BigDecimal BOND_TAX_RATE = GLOBAL_PROFIT_TAX_RATE;

  public static final BigDecimal RENTAL_TAX_RATE = new BigDecimal("0.085");

  private FinancialPolicyDefaults() {}
}
