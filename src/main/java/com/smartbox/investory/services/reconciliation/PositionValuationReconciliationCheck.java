package com.smartbox.investory.services.reconciliation;

import com.smartbox.investory.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionValuationReconciliationCheck implements ReconciliationCheck {
  private final ReconciliationReportRepository repository;

  @Override
  public ReconciliationCheckpoint checkpoint() {
    return ReconciliationCheckpoint.C3;
  }

  @Override
  public ReconciliationCheckResult execute(ReconciliationContext context) {
    var summary = repository.summarizePositionIssues();
    List<ReconciliationIssue> issues =
        repository.findPositionIssues().stream()
            .map(RepositoryReconciliationIssueMapper::position)
            .toList();
    long failures =
        summary == null
            ? issues.stream().filter(i -> i.status() == ReconciliationStatus.FAIL).count()
            : value(summary.getErrors());
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
        "v_position_valuation_validation",
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
