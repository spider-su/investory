package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.api.AccountingUserApi;

/** Typed UI boundary. The production adapter is in-process today and can become HTTP later. */
public interface AccountingRestClient extends AccountingUserApi, AccountingStagingApi {
  byte[] downloadJpk(long profileId, java.time.YearMonth month);
}
