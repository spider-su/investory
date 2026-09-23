package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.port.RyczaltCorrectionWriter;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRyczaltCorrectionWriter implements RyczaltCorrectionWriter {
  private final JdbcTemplate jdbc;

  public JdbcRyczaltCorrectionWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long write(
      long profileId,
      long originalPeriodId,
      String entityType,
      long entityId,
      String reason,
      Instant requestedAt,
      Long correctionPeriodId) {
    jdbc.update(
        "INSERT INTO investory.ryczalt_correction (profile_id, original_period_id,"
            + " affected_entity_type, affected_entity_id, reason, requested_at, correction_period_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        profileId,
        originalPeriodId,
        entityType,
        entityId,
        reason,
        requestedAt,
        correctionPeriodId);
    return jdbc.queryForObject(
        "SELECT currval(pg_get_serial_sequence('investory.ryczalt_correction', 'id'))", Long.class);
  }
}
