package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record PeriodResponse(
    YearMonth month,
    PeriodStatus periodStatus,
    List<CalculationResponse> calculations,
    String revenue,
    String ryczaltAmount,
    String vatAmount,
    String zusAmount,
    String totalObligations,
    int invoiceCount,
    int transactionCount,
    ObligationTotalsResponse obligations,
    CompletenessResponse completeness,
    Set<String> allowedActions) {
  public record ObligationTotalsResponse(
      int expectedCount, int paidCount, String paidAmount, String outstandingAmount) {}

  public record CompletenessResponse(String status, int issueCount) {}
}
