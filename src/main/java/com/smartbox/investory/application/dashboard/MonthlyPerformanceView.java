package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.services.models.MonthlyAttribution;
import java.util.Map;

public record MonthlyPerformanceView(
    Map<String, Double> calculateMonthlyPerformance,
    Map<String, Long> monthlyOperationsCount,
    Map<String, Double> monthlyCashflow,
    Map<String, MonthlyAttribution> monthlyAttributions) {

  public MonthlyPerformanceView {
    calculateMonthlyPerformance = copy(calculateMonthlyPerformance);
    monthlyOperationsCount =
        monthlyOperationsCount == null ? Map.of() : Map.copyOf(monthlyOperationsCount);
    monthlyCashflow = copy(monthlyCashflow);
    monthlyAttributions = monthlyAttributions == null ? Map.of() : Map.copyOf(monthlyAttributions);
  }

  private static <T> Map<String, T> copy(Map<String, T> source) {
    return source == null ? Map.of() : Map.copyOf(source);
  }
}
