package com.smartbox.investory.services.reconciliation;

import com.smartbox.investory.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountDailyReconciliationCheck implements ReconciliationCheck {
  private final ReconciliationReportRepository repository;

  @Override
  public ReconciliationCheckpoint checkpoint() {
    return ReconciliationCheckpoint.C4;
  }

  @Override
  public ReconciliationCheckResult execute(ReconciliationContext context) {
    var summary = repository.summarizeAccountIssues();
    List<ReconciliationIssue> issues =
        repository.findAccountIssues().stream()
            .map(RepositoryReconciliationIssueMapper::account)
            .toList();
    long failures =
        summary == null
            ? issues.stream().filter(i -> i.status() == ReconciliationStatus.FAIL).count()
            : value(summary.getFailures());
    long reviews =
        summary == null
            ? issues.stream().filter(i -> i.status() == ReconciliationStatus.REVIEW).count()
            : value(summary.getWarnings());
    long total = summary == null ? issues.size() : value(summary.getTotalIssues());
    return new ReconciliationCheckResult(
        checkpoint(),
        status(failures, reviews),
        total,
        failures,
        reviews,
        issues,
        "v_account_daily_reconciliation",
        Instant.now());
  }

  private static ReconciliationStatus status(long failures, long reviews) {
    if (failures > 0) return ReconciliationStatus.FAIL;
    if (reviews > 0) return ReconciliationStatus.REVIEW;
    return ReconciliationStatus.PASS;
  }

  private static long value(Long value) {
    return value == null ? 0 : value;
  }
}
