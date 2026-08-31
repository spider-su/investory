package com.smartbox.investory.investment.api.reporting.model;

import java.util.Map;

public record MonthlyPerformanceView(
    Map<String, Double> calculateMonthlyPerformance,
    Map<String, Long> monthlyOperationsCount,
    Map<String, Double> monthlyCashflow,
    Map<String, MonthlyAttribution> monthlyAttributions) {

  public MonthlyPerformanceView {
    calculateMonthlyPerformance = copy(calculateMonthlyPerformance);
    monthlyOperationsCount =
        com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(
            monthlyOperationsCount);
    monthlyCashflow = copy(monthlyCashflow);
    monthlyAttributions =
        com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(monthlyAttributions);
  }

  private static <T> Map<String, T> copy(Map<String, T> source) {
    return com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(source);
  }
}
