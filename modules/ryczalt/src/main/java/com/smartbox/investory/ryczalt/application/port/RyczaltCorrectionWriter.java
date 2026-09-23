package com.smartbox.investory.ryczalt.application.port;

import java.time.Instant;

/** Persists a correction request without exposing the storage technology to the application. */
public interface RyczaltCorrectionWriter {
  long write(
      long profileId,
      long originalPeriodId,
      String entityType,
      long entityId,
      String reason,
      Instant requestedAt,
      Long correctionPeriodId);
}
