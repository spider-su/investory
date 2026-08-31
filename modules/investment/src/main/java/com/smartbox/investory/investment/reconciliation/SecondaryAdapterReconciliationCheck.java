package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
import com.smartbox.investory.investment.port.export.SecondaryAdapterStatusReader;
import java.util.List;
import org.springframework.stereotype.Component;

/** Verifies that a generated secondary-adapter snapshot still represents current portfolio data. */
@Component
final class SecondaryAdapterReconciliationCheck implements ReconciliationCheck {

  private final SecondaryAdapterStatusReader statusReader;

  SecondaryAdapterReconciliationCheck(SecondaryAdapterStatusReader statusReader) {
    this.statusReader = statusReader;
  }

  @Override
  public ReconciliationCheckpoint checkpoint() {
    return ReconciliationCheckpoint.C7;
  }

  @Override
  public ReconciliationCheckResult execute(ReconciliationContext context) {
    SecondaryAdapterStatusReader.ExportStatus export = statusReader.status();
    if (export.lastExport() == null) {
      return result(
          ReconciliationStatus.REVIEW,
          "YAHOO_EXPORT_NOT_CREATED",
          "No Yahoo export snapshot exists",
          "Generate an export before release reconciliation.",
          context.startedAt());
    }
    if (!export.upToDate()) {
      return result(
          ReconciliationStatus.FAIL,
          "YAHOO_EXPORT_STALE",
          "Yahoo export differs from current portfolio positions or cash",
          "Regenerate the Yahoo export.",
          context.startedAt());
    }
    return new ReconciliationCheckResult(
        checkpoint(),
        ReconciliationStatus.PASS,
        0,
        0,
        0,
        List.of(),
        "yahoo_export_state + current Yahoo export payload",
        context.startedAt());
  }

  private ReconciliationCheckResult result(
      ReconciliationStatus status,
      String code,
      String details,
      String action,
      java.time.Instant executedAt) {
    ReconciliationIssue issue =
        new ReconciliationIssue(
            status,
            checkpoint(),
            "Yahoo portfolio export",
            code,
            "Secondary adapter snapshot",
            null,
            null,
            null,
            details,
            details,
            action);
    return new ReconciliationCheckResult(
        checkpoint(),
        status,
        1,
        status == ReconciliationStatus.FAIL ? 1 : 0,
        status == ReconciliationStatus.REVIEW ? 1 : 0,
        List.of(issue),
        "yahoo_export_state + current Yahoo export payload",
        executedAt);
  }
}
