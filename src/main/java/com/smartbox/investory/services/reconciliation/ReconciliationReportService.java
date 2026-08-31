package com.smartbox.investory.services.reconciliation;

import com.smartbox.investory.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import com.smartbox.investory.services.reconciliation.ReconciliationReport.Checkpoint;
import com.smartbox.investory.services.reconciliation.ReconciliationReport.Issue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationReportService {
  private final List<ReconciliationCheck> checks;
  private final Clock clock;

  @Autowired
  public ReconciliationReportService(
      List<ReconciliationCheck> discoveredChecks, JdbcTemplate jdbcTemplate, Clock clock) {
    List<ReconciliationCheck> all = new ArrayList<>(discoveredChecks);
    for (ReconciliationCheckpoint checkpoint :
        List.of(
            ReconciliationCheckpoint.C0,
            ReconciliationCheckpoint.C1,
            ReconciliationCheckpoint.C2,
            ReconciliationCheckpoint.C5)) {
      all.add(DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbcTemplate, checkpoint));
    }
    this.checks =
        all.stream()
            .sorted(Comparator.comparingInt(check -> check.checkpoint().ordinal()))
            .toList();
    this.clock = clock;
  }

  /** Compatibility constructor for focused service tests and non-Spring callers. */
  public ReconciliationReportService(ReconciliationReportRepository repository) {
    this.checks =
        List.of(
            new PositionValuationReconciliationCheck(repository),
            new AccountDailyReconciliationCheck(repository));
    this.clock = Clock.systemDefaultZone();
  }

  @Transactional(readOnly = true)
  public ReconciliationReport load() {
    Instant startedAt = clock.instant();
    return load(
        new ReconciliationContext(ReconciliationMode.QUICK, startedAt, LocalDate.now(clock)));
  }

  @Transactional(readOnly = true)
  public ReconciliationReport load(ReconciliationContext context) {
    Instant reportStarted = context.startedAt() == null ? clock.instant() : context.startedAt();
    Map<ReconciliationCheckpoint, List<ReconciliationCheckResult>> results =
        new EnumMap<>(ReconciliationCheckpoint.class);
    for (ReconciliationCheck check : checks) {
      try {
        ReconciliationCheckResult result = check.execute(context);
        results.computeIfAbsent(check.checkpoint(), ignored -> new ArrayList<>()).add(result);
      } catch (Exception exception) {
        ReconciliationCheckpoint checkpoint = check.checkpoint();
        ReconciliationIssue issue =
            new ReconciliationIssue(
                ReconciliationStatus.FAIL,
                checkpoint,
                checkpoint.displayName(),
                "CHECK_EXECUTION_ERROR",
                "Check execution",
                null,
                null,
                null,
                "Reconciliation check could not execute",
                safeMessage(exception),
                "Inspect the check evidence and database state.");
        results
            .computeIfAbsent(checkpoint, ignored -> new ArrayList<>())
            .add(
                new ReconciliationCheckResult(
                    checkpoint,
                    ReconciliationStatus.FAIL,
                    1,
                    1,
                    0,
                    List.of(issue),
                    "check execution",
                    clock.instant()));
      }
    }

    Map<ReconciliationCheckpoint, Aggregate> aggregates =
        new EnumMap<>(ReconciliationCheckpoint.class);
    results.forEach((checkpoint, parts) -> aggregates.put(checkpoint, Aggregate.of(parts)));
    int firstFailureRank =
        aggregates.entrySet().stream()
            .filter(entry -> entry.getValue().status() == ReconciliationStatus.FAIL)
            .mapToInt(entry -> entry.getKey().ordinal())
            .min()
            .orElse(Integer.MAX_VALUE);

    List<Issue> issues =
        results.values().stream()
            .flatMap(List::stream)
            .flatMap(result -> result.issues().stream())
            .map(issue -> toPresentationIssue(issue, firstFailureRank))
            .sorted(
                Comparator.comparingInt((Issue issue) -> issue.checkpoint().ordinal())
                    .thenComparing(issue -> statusRank(issue.effectiveStatus()))
                    .thenComparing(Issue::location))
            .toList();

    List<Checkpoint> checkpoints =
        List.of(ReconciliationCheckpoint.values()).stream()
            .map(checkpoint -> checkpoint(checkpoint, aggregates.get(checkpoint), firstFailureRank))
            .toList();
    Checkpoint firstFailing =
        checkpoints.stream()
            .filter(checkpoint -> checkpoint.originalStatus() == ReconciliationStatus.FAIL)
            .findFirst()
            .orElse(null);
    return new ReconciliationReport(
        overallState(checkpoints),
        firstFailing,
        checkpoints,
        issueGroups(issues),
        issues,
        context.mode(),
        reportStarted,
        context.asOfDate());
  }

  private static Checkpoint checkpoint(
      ReconciliationCheckpoint checkpoint, Aggregate aggregate, int firstFailureRank) {
    if (aggregate == null) {
      return new Checkpoint(
          checkpoint,
          checkpoint.displayName(),
          ReconciliationStatus.NOT_CHECKED,
          effective(ReconciliationStatus.NOT_CHECKED, checkpoint, firstFailureRank),
          0,
          0,
          0);
    }
    return new Checkpoint(
        checkpoint,
        checkpoint.displayName(),
        aggregate.status(),
        effective(aggregate.status(), checkpoint, firstFailureRank),
        aggregate.issueCount(),
        aggregate.failureCount(),
        aggregate.reviewCount());
  }

  private static ReconciliationStatus effective(
      ReconciliationStatus original, ReconciliationCheckpoint checkpoint, int firstFailureRank) {
    return checkpoint.ordinal() > firstFailureRank
            && (original == ReconciliationStatus.FAIL || original == ReconciliationStatus.REVIEW)
        ? ReconciliationStatus.BLOCKED
        : original;
  }

  private static Issue toPresentationIssue(ReconciliationIssue issue, int firstFailureRank) {
    return new Issue(
        issue.status(),
        effective(issue.status(), issue.checkpoint(), firstFailureRank),
        issue.checkpoint(),
        issue.location(),
        issue.checkCode(),
        issue.checkName(),
        issue.expected(),
        issue.actual(),
        issue.difference(),
        issue.cause(),
        issue.details(),
        issue.suggestedAction());
  }

  private static String overallState(List<Checkpoint> checkpoints) {
    if (checkpoints.stream().anyMatch(c -> c.originalStatus() == ReconciliationStatus.FAIL)) {
      return "UNRECONCILED";
    }
    if (checkpoints.stream()
        .anyMatch(
            c ->
                c.originalStatus() == ReconciliationStatus.REVIEW
                    || c.originalStatus() == ReconciliationStatus.NOT_CHECKED)) return "REVIEW";
    return "RECONCILED";
  }

  private static List<ReconciliationReport.IssueGroup> issueGroups(List<Issue> issues) {
    return issues.stream()
        .collect(Collectors.groupingBy(Issue::checkpoint, LinkedHashMap::new, Collectors.toList()))
        .entrySet()
        .stream()
        .map(entry -> new ReconciliationReport.IssueGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static int statusRank(ReconciliationStatus status) {
    return switch (status) {
      case FAIL -> 0;
      case BLOCKED -> 1;
      case REVIEW -> 2;
      default -> 3;
    };
  }

  private static String safeMessage(Exception exception) {
    String message = exception.getMessage();
    return message == null
        ? exception.getClass().getSimpleName()
        : message.replaceAll("(?i)(key|token|secret|password)=[^, ]+", "$1=[redacted]");
  }

  private record Aggregate(
      long issueCount, long failureCount, long reviewCount, ReconciliationStatus status) {
    static Aggregate of(List<ReconciliationCheckResult> parts) {
      long issues = parts.stream().mapToLong(ReconciliationCheckResult::issueCount).sum();
      long failures = parts.stream().mapToLong(ReconciliationCheckResult::failureCount).sum();
      long reviews = parts.stream().mapToLong(ReconciliationCheckResult::reviewCount).sum();
      ReconciliationStatus status =
          parts.stream()
              .map(ReconciliationCheckResult::status)
              .min(Comparator.comparingInt(ReconciliationReportService::statusOrder))
              .orElse(ReconciliationStatus.NOT_CHECKED);
      return new Aggregate(issues, failures, reviews, status);
    }
  }

  private static int statusOrder(ReconciliationStatus status) {
    return switch (status) {
      case FAIL -> 0;
      case REVIEW -> 1;
      case NOT_CHECKED -> 2;
      case PASS -> 3;
      case BLOCKED -> 4;
    };
  }
}
