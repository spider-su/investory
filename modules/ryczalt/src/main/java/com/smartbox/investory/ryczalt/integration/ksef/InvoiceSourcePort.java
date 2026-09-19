package com.smartbox.investory.ryczalt.integration.ksef;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Acquires provider-neutral invoices for native Ryczalt import. */
public interface InvoiceSourcePort {
  List<InvoiceSourceRecord> fetch(KsefSyncCommand command);

  /** What to acquire: a target month and one or more perspectives. */
  record KsefSyncCommand(YearMonth month, Set<KsefSyncMode> modes) {}
}
