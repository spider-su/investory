package com.smartbox.investory.investment.notifications;

import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemAuditNotificationProducer {
  private final JdbcTemplate jdbcTemplate;
  private final NotificationEventPublisher events;

  public boolean publish(UUID auditId) {
    java.util.List<AuditFact> matches =
        jdbcTemplate.query(
            """
            SELECT run.id, run.trigger_source, run.error_count, run.warning_count, run.finished_at,
                   array_remove(array_agg(issue.check_code ORDER BY
                     CASE issue.severity WHEN 'ERROR' THEN 0 ELSE 1 END,
                     issue.issue_count DESC, issue.check_code), NULL) AS check_codes
            FROM investory.system_audit_runs run
            LEFT JOIN investory.system_audit_issues issue ON issue.audit_run_id = run.id
            WHERE run.id = ? AND run.notification_status = 'READY_ERROR'
            GROUP BY run.id, run.trigger_source, run.error_count, run.warning_count, run.finished_at
            """,
            (rs, row) ->
                new AuditFact(
                    rs.getObject("id", UUID.class),
                    rs.getString("trigger_source"),
                    rs.getInt("error_count"),
                    rs.getInt("warning_count"),
                    instant(rs.getTimestamp("finished_at")),
                    codes(rs.getArray("check_codes"))),
            auditId);
    if (matches.isEmpty()) return false;
    AuditFact audit = matches.getFirst();

    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("auditId", audit.id().toString());
    payload.put("triggerSource", audit.triggerSource());
    payload.put("errorCount", Integer.toString(audit.errorCount()));
    payload.put("warningCount", Integer.toString(audit.warningCount()));
    payload.put("checkCodes", audit.checkCodes());
    return events.publish(
        new NotificationCandidate(
            NotificationEventType.SYSTEM_AUDIT_ERROR,
            NotificationSeverity.ERROR,
            null,
            "SYSTEM_AUDIT_RUN",
            audit.id().toString(),
            "SYSTEM_AUDIT_ERROR:" + audit.id(),
            "System audit requires action",
            payload,
            audit.finishedAt()));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? Instant.now() : value.toInstant();
  }

  private static String codes(Array value) throws java.sql.SQLException {
    if (value == null) return "Unavailable";
    Object raw = value.getArray();
    if (!(raw instanceof String[] codes)) return "Unavailable";
    return java.util.Arrays.stream(codes)
        .distinct()
        .limit(5)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private record AuditFact(
      UUID id,
      String triggerSource,
      int errorCount,
      int warningCount,
      Instant finishedAt,
      String checkCodes) {}
}
