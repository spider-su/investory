package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import java.util.EnumSet;
import java.util.List;

/** Single accounting rule used by both the freeze command and its read-side action list. */
public final class FreezeEligibility {
  private FreezeEligibility() {}

  public static boolean isEligible(
      RyczaltPeriodEntity period,
      List<RyczaltCalculationEntity> calculations,
      List<RyczaltObligationEntity> obligations) {
    if (!period.getStatus().canFreeze()) return false;
    boolean calculationsCurrent =
        EnumSet.allOf(CalculationType.class).stream()
            .allMatch(
                type ->
                    calculations.stream()
                        .anyMatch(
                            calculation ->
                                calculation.getType() == type
                                    && calculation.isCurrent()
                                    && calculation.getStatus() != CalculationStatus.DIRTY
                                    && calculation.getStatus() != CalculationStatus.STALE));
    if (!calculationsCurrent || obligations.size() != CalculationType.values().length) return false;
    return obligations.stream()
        .allMatch(
            obligation ->
                obligation.getStatus() == ObligationStatus.PAID
                    || obligation.getStatus() == ObligationStatus.OVERPAID);
  }
}
