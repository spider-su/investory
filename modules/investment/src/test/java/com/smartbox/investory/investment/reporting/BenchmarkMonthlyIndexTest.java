package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkMonthlyIndexTest {

  @Test
  void indexesRowsByMonthAndAccountOnceAndKeepsFirstDuplicate() {
    AccountMonthlyPerformanceEntity first = row(1L, "2026-01-01", 1000.0, 10.0);
    AccountMonthlyPerformanceEntity duplicate = row(1L, "2026-01-15", 2000.0, 20.0);
    AccountMonthlyPerformanceEntity second = row(2L, "2026-02-01", 500.0, 5.0);

    BenchmarkMonthlyIndex index =
        BenchmarkMonthlyIndex.create(
            List.of(first, duplicate, second), List.of("2026-01", "2026-02"));

    assertEquals(2, index.rowsByMonth().size());
    assertEquals(2, index.rowsByAccount().size());
    assertSame(first, index.rowsByAccount().get(1L).get(java.time.YearMonth.of(2026, 1)));
    assertEquals(2, index.rowsByMonth().get(java.time.YearMonth.of(2026, 1)).size());
  }

  private static AccountMonthlyPerformanceEntity row(
      Long accountId, String month, double startEquity, double profit) {
    LocalDate date = LocalDate.parse(month);
    return new AccountMonthlyPerformanceEntity(
        accountId + ":" + month,
        accountId,
        date,
        date.withDayOfMonth(date.lengthOfMonth()),
        startEquity,
        startEquity + profit,
        0.0,
        0.0,
        0.0,
        profit,
        profit / startEquity,
        ZonedDateTime.now());
  }
}
