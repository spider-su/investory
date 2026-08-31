package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
import com.smartbox.investory.investment.infrastructure.persistence.reconciliation.ReconciliationEvidenceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountDailyReconciliationCheck implements ReconciliationCheck {
  private final ReconciliationEvidenceRepository repository;

  @Override
  public ReconciliationCheckpoint checkpoint() {
    return ReconciliationCheckpoint.C4;
  }

  @Override
  public ReconciliationCheckResult execute(ReconciliationContext context) {
    var rows = repository.findAccountIssues();
    List<ReconciliationIssue> issues =
        rows.stream().map(RepositoryReconciliationIssueMapper::account).toList();
    long failures =
        rows.isEmpty()
            ? 0
            : valueOr(rows.getFirst().getFailures(), issues, ReconciliationStatus.FAIL);
    long reviews =
        rows.isEmpty()
            ? 0
            : valueOr(rows.getFirst().getReviews(), issues, ReconciliationStatus.REVIEW);
    long total = rows.isEmpty() ? 0 : valueOr(rows.getFirst().getTotalIssues(), issues, null);
    return new ReconciliationCheckResult(
        checkpoint(),
        status(failures, reviews),
        total,
        failures,
        reviews,
        issues,
        "v_account_daily_reconciliation",
        context.startedAt());
  }

  private static ReconciliationStatus status(long failures, long reviews) {
    if (failures > 0) return ReconciliationStatus.FAIL;
    if (reviews > 0) return ReconciliationStatus.REVIEW;
    return ReconciliationStatus.PASS;
  }

  private static long valueOr(
      Long value, List<ReconciliationIssue> issues, ReconciliationStatus status) {
    if (value != null) return value;
    return status == null
        ? issues.size()
        : issues.stream().filter(i -> i.status() == status).count();
  }
}
