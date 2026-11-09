package com.smartbox.investory.investment.api.operations;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/** Latest broker-import state used by operational adapters. */
public interface ImportOperationsReader {
  Optional<ImportOperationsSnapshot> latestImport(Long portfolioId);

  /** Latest attempt for each broker with import history in this portfolio. */
  List<ImportOperationsSnapshot> latestImportsByBroker(Long portfolioId);

  record ImportOperationsSnapshot(
      long batchId,
      ImportBroker broker,
      String status,
      ZonedDateTime startedAt,
      ZonedDateTime finishedAt) {}
}
