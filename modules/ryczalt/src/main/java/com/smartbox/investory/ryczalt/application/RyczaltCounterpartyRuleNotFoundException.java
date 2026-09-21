package com.smartbox.investory.ryczalt.application;

public class RyczaltCounterpartyRuleNotFoundException extends RuntimeException {
  public RyczaltCounterpartyRuleNotFoundException(long profileId, long ruleId) {
    super("Counterparty rule " + ruleId + " was not found for profile " + profileId);
  }
}
