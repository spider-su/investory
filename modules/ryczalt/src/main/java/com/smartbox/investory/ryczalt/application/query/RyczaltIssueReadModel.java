package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.checker.CheckSeverity;

public record RyczaltIssueReadModel(
    String id,
    String code,
    CheckSeverity severity,
    IssueKind kind,
    String title,
    String message,
    String sourceReference) {
  public RyczaltIssueReadModel(String code, CheckSeverity severity, String context) {
    this(
        code + ":" + context,
        code,
        severity,
        code.equals("UNSETTLED_OBLIGATION") ? IssueKind.SETTLEMENT : IssueKind.BLOCKED,
        code,
        context,
        context);
  }

  public String context() {
    return sourceReference;
  }
}
