package com.smartbox.investory.ryczalt.integration.bank;

import java.util.List;

/** Acquires provider-neutral bank transactions for native Ryczalt import. */
public interface BankTransactionSourcePort {
  List<BankTransactionSourceRecord> fetch(BankImportSource source);

  /** Raw bank export handed to the adapter. */
  record BankImportSource(byte[] content, String filename, String contentType) {}
}
