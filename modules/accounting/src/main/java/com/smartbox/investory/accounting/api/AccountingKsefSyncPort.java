package com.smartbox.investory.accounting.api;

import java.time.YearMonth;

/** Application boundary for the configured KSeF source adapter. */
public interface AccountingKsefSyncPort {
  AccountingUserApi.KsefSyncResult sync(long profileId, YearMonth month);

  /** Synchronizes incoming, seller-side (including corrections), and third-party evidence. */
  default AccountingUserApi.KsefSyncResult syncAll(long profileId, YearMonth month) {
    return sync(profileId, month);
  }

  default AccountingUserApi.KsefSyncResult reimport(long profileId, YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Evidence-backed KSeF re-import is not supported.");
  }

  default AccountingUserApi.KsefSyncResult syncSeller(long profileId, YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Seller-side KSeF sync is not supported.");
  }

  default AccountingUserApi.KsefSyncResult syncThirdParty(long profileId, YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Third-party KSeF sync is not supported.");
  }

  String providerStatus();
}
