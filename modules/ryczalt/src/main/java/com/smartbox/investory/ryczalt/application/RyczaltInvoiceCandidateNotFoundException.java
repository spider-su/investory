package com.smartbox.investory.ryczalt.application;

public class RyczaltInvoiceCandidateNotFoundException extends RuntimeException {
  public RyczaltInvoiceCandidateNotFoundException(long profileId) {
    super("Invoice candidate was not found for profile " + profileId);
  }
}
