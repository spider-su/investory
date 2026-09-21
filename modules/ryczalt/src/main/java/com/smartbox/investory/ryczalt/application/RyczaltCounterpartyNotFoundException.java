package com.smartbox.investory.ryczalt.application;

public class RyczaltCounterpartyNotFoundException extends RuntimeException {
  public RyczaltCounterpartyNotFoundException(long profileId, long counterpartyId) {
    super("Counterparty " + counterpartyId + " was not found for profile " + profileId);
  }
}
