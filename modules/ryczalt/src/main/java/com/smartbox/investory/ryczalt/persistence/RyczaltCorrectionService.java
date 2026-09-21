package com.smartbox.investory.ryczalt.persistence;

import java.time.Instant;
import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a correction request without mutating the original period. */
@Service
public class RyczaltCorrectionService {
  private final RyczaltPeriodJpaRepository periods;
  private final JdbcTemplate jdbc;

  public RyczaltCorrectionService(RyczaltPeriodJpaRepository periods, JdbcTemplate jdbc) {
    this.periods = periods;
    this.jdbc = jdbc;
  }

  @Transactional
  public long request(
      long profileId,
      YearMonth originalMonth,
      String entityType,
      long entityId,
      String reason,
      String actor,
      YearMonth correctionMonth) {
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("Reason is required");
    long originalPeriodId =
        periods
            .findByProfileIdAndYearAndMonth(
                profileId, originalMonth.getYear(), originalMonth.getMonthValue())
            .map(RyczaltPeriodEntity::id)
            .orElseThrow(() -> new IllegalArgumentException("Original period does not exist"));
    Long correctionPeriodId =
        correctionMonth == null
            ? null
            : periods
                .findByProfileIdAndYearAndMonth(
                    profileId, correctionMonth.getYear(), correctionMonth.getMonthValue())
                .map(RyczaltPeriodEntity::id)
                .orElseThrow(
                    () -> new IllegalArgumentException("Correction period does not exist"));
    jdbc.update(
        "INSERT INTO investory.ryczalt_correction (profile_id, original_period_id, entity_type,"
            + " entity_id, reason, requested_at, correction_period_id) VALUES (?, ?, ?, ?, ?, ?,"
            + " ?)",
        profileId,
        originalPeriodId,
        entityType,
        entityId,
        reason,
        Instant.now(),
        correctionPeriodId);
    return jdbc.queryForObject(
        "SELECT currval(pg_get_serial_sequence('investory.ryczalt_correction', 'id'))", Long.class);
  }
}
