package com.smartbox.investory.ryczalt.application;

/** Expected workflow conflict in native Ryczalt invoice processing. */
public class RyczaltInvoiceConflictException extends RuntimeException {
  public RyczaltInvoiceConflictException(String message) {
    super(message);
  }
}
