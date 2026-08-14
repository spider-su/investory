package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.longterm.*;
import com.smartbox.investory.services.FinancialPrecision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LongTermAssetCalculator {
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private LongTermAssetCalculator() {}

  public static BigDecimal annualAmount(LongTermAssetCashFlow flow) {
    return flow.getFrequency() == Frequency.MONTHLY
        ? flow.getAmount().multiply(TWELVE)
        : flow.getAmount();
  }

  public static boolean applies(LongTermAssetCashFlow flow, LocalDate date) {
    return !date.isBefore(flow.getValidFrom())
        && (flow.getValidTo() == null || !date.isAfter(flow.getValidTo()));
  }

  public static boolean applies(LocalDate from, LocalDate to, LocalDate date) {
    return !date.isBefore(from) && (to == null || !date.isAfter(to));
  }

  public static void validateNoOverlap(List<? extends Period> periods) {
    for (int i = 0; i < periods.size(); i++)
      for (int j = i + 1; j < periods.size(); j++)
        if (periods.get(i).overlaps(periods.get(j)))
          throw new IllegalArgumentException("Overlapping periods are not allowed");
  }

  public interface Period {
    LocalDate from();

    LocalDate to();

    default boolean overlaps(Period other) {
      return !from().isAfter(other.toOrMax()) && !other.from().isAfter(toOrMax());
    }

    default LocalDate toOrMax() {
      return to() == null ? LocalDate.MAX : to();
    }
  }

  public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    return FinancialPrecision.ratio(numerator, denominator);
  }
}
