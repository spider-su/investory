package com.smartbox.investory.ryczalt.application;

public class RyczaltInvoiceSourceConflictException extends RyczaltInvoiceConflictException {
  public RyczaltInvoiceSourceConflictException() {
    super("Invoice source already exists");
  }
}
