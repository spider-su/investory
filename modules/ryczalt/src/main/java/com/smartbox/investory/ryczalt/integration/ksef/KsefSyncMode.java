package com.smartbox.investory.ryczalt.integration.ksef;

/** Native KSeF acquisition perspective. */
public enum KsefSyncMode {
  /** Invoices the taxpayer issued (canonical INCOME). */
  SALES,
  /** Invoices issued to the taxpayer (canonical COST). */
  PURCHASES
}
