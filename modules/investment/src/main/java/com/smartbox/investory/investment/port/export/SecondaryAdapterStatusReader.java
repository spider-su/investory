package com.smartbox.investory.investment.port.export;

import java.time.ZonedDateTime;

/** Read boundary for operational checks of generated secondary-adapter snapshots. */
public interface SecondaryAdapterStatusReader {

  ExportStatus status();

  /**
   * Returns status for the supplied portfolio when the adapter can compare current payload data.
   */
  default ExportStatus status(Long portfolioId) {
    return status();
  }

  record ExportStatus(ZonedDateTime lastExport, boolean upToDate) {}
}
