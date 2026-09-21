package com.smartbox.investory.ryczalt.application.bank;

/** Native Ryczalt bank acquisition boundary used by native REST and compatibility adapters. */
public interface RyczaltBankApi {
  RyczaltBankImportResult importBank(
      long profileId, byte[] content, String filename, String contentType);
}
