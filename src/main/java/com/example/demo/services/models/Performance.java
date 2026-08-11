package com.example.demo.services.models;

import com.example.demo.infrastructure.CurrencyType;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Performance {
  CurrencyType baseCurrency = CurrencyType.USD;

  double totalOpen = 0.0;

  double totalProfit = 0.0;

  Map<String, Double> calculateMonthlyPerformance = new TreeMap<>();

  Map<String, Long> monthlyOperationsCount = new TreeMap<>();

  Map<String, Double> monthlyCashflow = new TreeMap<>();
  Map<String, MonthlyAttribution> monthlyAttributions = new TreeMap<>();

  double base = 0.0;
}
