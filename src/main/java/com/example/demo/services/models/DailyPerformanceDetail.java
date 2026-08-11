package com.example.demo.services.models;

import java.time.LocalDate;
import java.util.List;

public record DailyPerformanceDetail(
    LocalDate date,
    double dailyProfit,
    double dailyReturnPct,
    double openingEquity,
    double closingEquity,
    double deposits,
    double withdrawals,
    double dividends,
    double interest,
    double fees,
    double taxes,
    double unresolvedResidual,
    List<AccountRow> accounts,
    String attributionNote) {
  public record AccountRow(
      Long accountId,
      double openingEquity,
      double closingEquity,
      double dailyProfit,
      double deposits,
      double withdrawals) {}
}
