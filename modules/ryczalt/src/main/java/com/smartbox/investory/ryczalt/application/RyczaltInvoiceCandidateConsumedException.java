package com.smartbox.investory.ryczalt.application;

public class RyczaltInvoiceCandidateConsumedException extends RyczaltInvoiceConflictException {
  public RyczaltInvoiceCandidateConsumedException() {
    super("Invoice candidate was already consumed");
  }
}
