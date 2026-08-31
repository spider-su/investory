package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;

final class PlanningTimelineValueSupport {
  private PlanningTimelineValueSupport() {}

  static PlanningMetricValue unavailable(PlanningMetric metric) {
    return new PlanningMetricValue(
        metric, null, null, PlanningValueSource.UNAVAILABLE, unavailableNote(metric));
  }

  static BigDecimal grow(BigDecimal amount, BigDecimal rate) {
    return amount.multiply(BigDecimal.ONE.add(rate));
  }

  static BigDecimal growForYears(BigDecimal amount, BigDecimal rate, int years) {
    BigDecimal result = amount;
    for (int year = 0; year < Math.max(0, years); year++) result = grow(result, rate);
    return result;
  }

  static int age(SimulationAssumptions assumptions, int year) {
    return assumptions.currentAge() + year - assumptions.startYear();
  }

  private static String unavailableNote(PlanningMetric metric) {
    return switch (metric) {
      case CORE_SPENDING, DISCRETIONARY_SPENDING -> "Required historical planning input";
      case NET_WORTH -> "Optional; Market assets provide closure anchor";
      case REAL_ESTATE, BOND_VALUE, BOND_INCOME, CASH_RESERVE_VALUE, RENTAL_INCOME ->
          "Historical value unavailable";
      default -> "Historical value unavailable";
    };
  }
}
