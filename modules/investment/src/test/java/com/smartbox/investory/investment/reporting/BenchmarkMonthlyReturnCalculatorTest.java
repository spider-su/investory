package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkMonthlyReturnCalculatorTest {

  @Test
  void linksWeightedMonthlyReturnsUsingTheIndexedRows() {
    AccountMonthlyPerformanceEntity first = row(1L, "2026-01-01", 1000.0, 0.10);
    AccountMonthlyPerformanceEntity second = row(2L, "2026-01-01", 3000.0, 0.20);
    AccountMonthlyPerformanceEntity next = row(1L, "2026-02-01", 1000.0, 0.10);

    BenchmarkMonthlyIndex index =
        BenchmarkMonthlyIndex.create(List.of(first, second, next), List.of("2026-01", "2026-02"));

    assertEquals(
        List.of(17.5, 29.25), BenchmarkMonthlyReturnCalculator.portfolioReturnCurve(index));
  }

  private static AccountMonthlyPerformanceEntity row(
      Long accountId, String month, double startEquity, double returnPct) {
    LocalDate date = LocalDate.parse(month);
    double profit = startEquity * returnPct;
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
        returnPct,
        ZonedDateTime.now());
  }
}
