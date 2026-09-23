package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.port.RyczaltCorrectionWriter;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a correction request without mutating the original period. */
@Service
public class RyczaltCorrectionService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltCorrectionWriter writer;

  public RyczaltCorrectionService(
      RyczaltPeriodJpaRepository periods, RyczaltCorrectionWriter writer) {
    this.periods = periods;
    this.writer = writer;
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
    return writer.write(
        profileId,
        originalPeriodId,
        entityType,
        entityId,
        reason,
        Instant.now(),
        correctionPeriodId);
  }
}
