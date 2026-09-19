package com.smartbox.investory.ryczalt.calculation;

import com.smartbox.investory.ryczalt.persistence.CalculationType;
import java.util.EnumSet;
import java.util.Set;

/** Explicit dependency map; unrelated changes do not invalidate a calculation. */
public final class CalculationInvalidationPolicy {
  private CalculationInvalidationPolicy() {}

  public static Set<CalculationType> affectedBy(InputChange change) {
    return switch (change) {
      case INCOME_INVOICE_CHANGED -> EnumSet.of(CalculationType.RYCZALT, CalculationType.VAT);
      case COST_INVOICE_CHANGED -> EnumSet.of(CalculationType.VAT);
      case ZUS_INPUT_CHANGED -> EnumSet.of(CalculationType.ZUS, CalculationType.RYCZALT);
      case FX_FACT_CHANGED -> EnumSet.of(CalculationType.RYCZALT, CalculationType.VAT);
      case TRANSACTION_CHANGED -> EnumSet.noneOf(CalculationType.class);
    };
  }
}
