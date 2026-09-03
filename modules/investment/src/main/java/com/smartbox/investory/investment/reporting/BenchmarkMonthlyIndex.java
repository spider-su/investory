package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable, month-positioned view of the rows used by monthly benchmark reporting. */
record BenchmarkMonthlyIndex(
    List<String> labels,
    Map<YearMonth, List<AccountMonthlyPerformanceEntity>> rowsByMonth,
    Map<Long, Map<YearMonth, AccountMonthlyPerformanceEntity>> rowsByAccount) {

  static BenchmarkMonthlyIndex create(
      List<AccountMonthlyPerformanceEntity> rows, List<String> labels) {
    Map<YearMonth, List<AccountMonthlyPerformanceEntity>> byMonth =
        rows.stream()
            .filter(row -> row.getMonth() != null)
            .collect(Collectors.groupingBy(row -> YearMonth.from(row.getMonth())));
    Map<Long, Map<YearMonth, AccountMonthlyPerformanceEntity>> byAccount = new LinkedHashMap<>();
    for (AccountMonthlyPerformanceEntity row : rows) {
      if (row.getAccountId() == null || row.getMonth() == null) continue;
      byAccount
          .computeIfAbsent(row.getAccountId(), ignored -> new LinkedHashMap<>())
          .putIfAbsent(YearMonth.from(row.getMonth()), row);
    }
    return new BenchmarkMonthlyIndex(
        List.copyOf(labels), Map.copyOf(byMonth), Map.copyOf(byAccount));
  }
}
