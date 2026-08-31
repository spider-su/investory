package com.smartbox.investory.investment.api.operations;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import java.time.ZonedDateTime;
import java.util.Optional;

/** Latest broker-import state used by operational adapters. */
public interface ImportOperationsReader {
  Optional<ImportOperationsSnapshot> latestImport();

  record ImportOperationsSnapshot(
      long batchId,
      ImportBroker broker,
      String status,
      ZonedDateTime startedAt,
      ZonedDateTime finishedAt) {}
}
