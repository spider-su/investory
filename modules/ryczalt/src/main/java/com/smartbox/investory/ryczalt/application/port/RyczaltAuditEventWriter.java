package com.smartbox.investory.ryczalt.application.port;

import java.time.Instant;

/** Persistence boundary for the native Ryczalt audit trail. */
public interface RyczaltAuditEventWriter {
  void write(
      long profileId,
      Long periodId,
      String eventType,
      String reason,
      String actor,
      Instant occurredAt);
}
