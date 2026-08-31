package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit inventory of application and database-backed reconciliation checks. */
@Component
final class ReconciliationCheckRegistry {
  private static final List<ReconciliationCheckpoint> DATABASE_CHECKPOINTS =
      List.of(
          ReconciliationCheckpoint.C0,
          ReconciliationCheckpoint.C1,
          ReconciliationCheckpoint.C2,
          ReconciliationCheckpoint.C5,
          ReconciliationCheckpoint.C6);

  private final List<ReconciliationCheck> checks;

  ReconciliationCheckRegistry(
      List<ReconciliationCheck> discoveredChecks, JdbcTemplate jdbcTemplate) {
    List<ReconciliationCheck> all = new ArrayList<>(discoveredChecks);
    DATABASE_CHECKPOINTS.stream()
        .map(
            checkpoint ->
                DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbcTemplate, checkpoint))
        .forEach(all::add);
    this.checks = all.stream().toList();
  }

  List<ReconciliationCheck> checks() {
    return checks;
  }
}
