package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record PeriodResponse(
    YearMonth month,
    PeriodStatus status,
    List<CalculationResponse> calculations,
    SummaryResponse summary,
    DocumentsResponse documents,
    SettlementResponse settlement,
    ReconciliationResponse reconciliation,
    CompletenessResponse completeness,
    Set<String> allowedActions) {
  public record SummaryResponse(String revenue, String ryczalt, String vat, String zus) {}

  public record DocumentsResponse(int invoiceCount, int transactionCount) {}

  public record SettlementResponse(
      int expectedCount,
      int paidCount,
      int outstandingCount,
      String totalExpected,
      String totalPaid,
      String totalOutstanding,
      boolean fullySettled) {}

  public record ReconciliationResponse(
      int rowCount, int settledCount, int mismatchCount, int missingEvidenceCount) {}

  public record CompletenessResponse(String status, int blockingIssueCount) {
    public int issueCount() {
      return blockingIssueCount;
    }
  }
}
