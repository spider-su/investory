package com.smartbox.investory.services.reconciliation;

import com.smartbox.investory.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RepositoryReconciliationIssueMapper {
  private RepositoryReconciliationIssueMapper() {}

  static ReconciliationIssue position(ReconciliationReportRepository.PositionIssueRow row) {
    ReconciliationStatus status =
        "ERROR".equalsIgnoreCase(row.getSeverity())
            ? ReconciliationStatus.FAIL
            : ReconciliationStatus.REVIEW;
    return new ReconciliationIssue(
        status,
        ReconciliationCheckpoint.C3,
        location(row.getProvider(), row.getAccountName(), row.getSymbol(), row.getValuationDate()),
        row.getValidationCode(),
        row.getValidationCode(),
        row.getExpectedValue(),
        row.getActualValue(),
        row.getDifference(),
        row.getMessage(),
        "Relative difference: " + value(row.getRelativeDifference()),
        null);
  }

  static ReconciliationIssue account(ReconciliationReportRepository.AccountIssueRow row) {
    AccountComponent component = component(row);
    ReconciliationStatus status =
        "FAIL".equalsIgnoreCase(row.getStatus())
            ? ReconciliationStatus.FAIL
            : ReconciliationStatus.REVIEW;
    return new ReconciliationIssue(
        status,
        ReconciliationCheckpoint.C4,
        location(row.getProvider(), row.getAccountName(), null, row.getValuationDate()),
        row.getDiagnosticCode() == null || row.getDiagnosticCode().isBlank()
            ? "UNKNOWN"
            : row.getDiagnosticCode(),
        component.name(),
        component.expected(),
        component.actual(),
        component.difference(),
        row.getValidationMessage(),
        "Market Δ: "
            + value(row.getMarketValueDifference())
            + " · Cash Δ: "
            + value(row.getCashDifference())
            + " · Equity Δ: "
            + value(row.getEquityDifference())
            + " · Cost base Δ: "
            + value(row.getCostBaseDifference())
            + " · Unrealized Δ: "
            + value(row.getUnrealizedDifference())
            + " · Realized Δ: "
            + value(row.getRealizedDifference()),
        component.suggestedAction());
  }

  private static AccountComponent component(ReconciliationReportRepository.AccountIssueRow row) {
    String code = row.getDiagnosticCode();
    if (code == null || code.isBlank()) {
      return new AccountComponent(
          "Unknown diagnostic code",
          null,
          null,
          null,
          "Inspect reconciliation view diagnostic_code.");
    }
    return switch (code) {
      case "ACCOUNT_DAILY_CASH_RECONCILIATION" ->
          new AccountComponent(
              "Cash reconciliation",
              row.getExpectedCashBalance(),
              row.getActualCashBalance(),
              row.getCashDifference(),
              "Inspect cash ledger inputs.");
      case "ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION", "MARKET_VALUE_SEMANTIC_REVIEW" ->
          new AccountComponent(
              "Market value reconciliation",
              row.getExpectedMarketValue(),
              row.getActualMarketValue(),
              row.getMarketValueDifference(),
              "Inspect position valuation inputs.");
      case "ACCOUNT_DAILY_COST_BASE_RECONCILIATION" ->
          new AccountComponent(
              "Cost-base reconciliation",
              row.getExpectedCostBase(),
              row.getActualCostBase(),
              row.getCostBaseDifference(),
              "Inspect trade cost basis.");
      case "ACCOUNT_DAILY_UNREALIZED_RECONCILIATION" ->
          new AccountComponent(
              "Unrealized P/L reconciliation",
              row.getExpectedUnrealized(),
              row.getActualUnrealized(),
              row.getUnrealizedDifference(),
              "Inspect position reconstruction.");
      case "ACCOUNT_DAILY_REALIZED_RECONCILIATION" ->
          new AccountComponent(
              "Realized P/L reconciliation",
              row.getExpectedRealized(),
              row.getActualRealized(),
              row.getRealizedDifference(),
              "Inspect realized-result inputs.");
      case "ACCOUNT_DAILY_EQUITY_RECONCILIATION" ->
          new AccountComponent(
              "Equity reconciliation",
              row.getExpectedEquity(),
              row.getActualEquity(),
              row.getEquityDifference(),
              "Inspect cash and position reconciliation.");
      default ->
          new AccountComponent(
              "Unknown diagnostic code: " + code,
              null,
              null,
              null,
              "Inspect the source reconciliation diagnostic.");
    };
  }

  private static String location(String provider, String account, String asset, Object date) {
    return Stream.of(provider, account, asset, date == null ? null : date.toString())
        .filter(value -> value != null && !value.isBlank())
        .collect(Collectors.joining(" / "));
  }

  private static String value(BigDecimal value) {
    return value == null ? "—" : value.toPlainString();
  }

  private record AccountComponent(
      String name,
      BigDecimal expected,
      BigDecimal actual,
      BigDecimal difference,
      String suggestedAction) {}
}
