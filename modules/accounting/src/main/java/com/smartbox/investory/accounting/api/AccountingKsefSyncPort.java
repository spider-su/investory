package com.smartbox.investory.accounting.api;

import java.time.YearMonth;

/** Application boundary for the configured KSeF source adapter. */
public interface AccountingKsefSyncPort {
  AccountingUserApi.KsefSyncResult sync(YearMonth month);

  String providerStatus();
}
