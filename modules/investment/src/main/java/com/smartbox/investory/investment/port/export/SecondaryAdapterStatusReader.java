package com.smartbox.investory.investment.port.export;

import java.time.ZonedDateTime;

/** Read boundary for operational checks of generated secondary-adapter snapshots. */
public interface SecondaryAdapterStatusReader {

  ExportStatus status();

  record ExportStatus(ZonedDateTime lastExport, boolean upToDate) {}
}
