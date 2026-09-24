package com.smartbox.investory.integrations.ksef;

import java.time.YearMonth;

/** Application bridge used by the managed KSeF integration job. */
public interface KsefSyncJobPort {
  void sync(YearMonth month);
}
