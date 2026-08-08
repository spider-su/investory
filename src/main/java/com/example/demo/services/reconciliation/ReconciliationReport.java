package com.example.demo.services.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationReport(
    PositionSummary positionSummary,
    AccountSummary accountSummary,
    List<PositionIssue> positionIssues,
    List<AccountIssue> accountIssues) {

  public record PositionSummary(
      long totalIssues, long errors, long warnings, long affectedAssets, long affectedAccounts) {}

  public record AccountSummary(
      long totalIssues, long failures, long warnings, long affectedAccounts) {}

  public record PositionIssue(
      Long accountId,
      String accountName,
      String provider,
      Long assetId,
      String symbol,
      LocalDate valuationDate,
      String severity,
      String validationCode,
      BigDecimal expectedValue,
      BigDecimal actualValue,
      BigDecimal difference,
      BigDecimal relativeDifference,
      String message) {}

  public record AccountIssue(
      Long accountId,
      String accountName,
      String provider,
      LocalDate valuationDate,
      String status,
      String severity,
      String validationMessage,
      BigDecimal marketValueDifference,
      BigDecimal cashDifference,
      BigDecimal equityDifference,
      BigDecimal costBaseDifference,
      BigDecimal unrealizedDifference) {}
}
