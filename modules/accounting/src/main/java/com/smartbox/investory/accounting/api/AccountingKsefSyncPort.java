package com.smartbox.investory.accounting.api;

import java.time.YearMonth;

/** Application boundary for the configured KSeF source adapter. */
public interface AccountingKsefSyncPort {
  AccountingUserApi.KsefSyncResult sync(YearMonth month);

  /** Synchronizes incoming, seller-side (including corrections), and third-party evidence. */
  default AccountingUserApi.KsefSyncResult syncAll(YearMonth month) {
    return sync(month);
  }

  default AccountingUserApi.KsefSyncResult reimport(YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Evidence-backed KSeF re-import is not supported.");
  }

  default AccountingUserApi.KsefSyncResult syncSeller(YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Seller-side KSeF sync is not supported.");
  }

  default AccountingUserApi.KsefSyncResult syncThirdParty(YearMonth month) {
    return new AccountingUserApi.KsefSyncResult(
        "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Third-party KSeF sync is not supported.");
  }

  String providerStatus();
}
