package com.smartbox.investory.ryczalt.application.ksef;

import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import java.time.YearMonth;
import java.util.Set;

/** Native Ryczalt KSeF acquisition boundary used by native REST and compatibility adapters. */
public interface RyczaltKsefApi {
  RyczaltKsefSyncResult sync(long profileId, YearMonth month, Set<KsefSyncMode> modes);

  RyczaltKsefSyncResult reimport(long profileId, YearMonth month);
}
