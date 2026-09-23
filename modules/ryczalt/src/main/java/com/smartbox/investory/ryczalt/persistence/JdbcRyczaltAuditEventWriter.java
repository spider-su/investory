package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.port.RyczaltAuditEventWriter;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for the native audit table. */
@Repository
public class JdbcRyczaltAuditEventWriter implements RyczaltAuditEventWriter {
  private final JdbcTemplate jdbc;

  public JdbcRyczaltAuditEventWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void write(
      long profileId,
      Long periodId,
      String eventType,
      String reason,
      String actor,
      Instant occurredAt) {
    jdbc.update(
        "INSERT INTO investory.ryczalt_audit_event (profile_id, period_id, event_type, reason,"
            + " actor, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
        profileId,
        periodId,
        eventType,
        reason,
        actor,
        Timestamp.from(occurredAt));
  }
}
