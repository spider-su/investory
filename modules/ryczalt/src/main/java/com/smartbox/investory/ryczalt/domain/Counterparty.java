package com.smartbox.investory.ryczalt.domain;

public record Counterparty(
    long id, long profileId, String taxIdentifier, String country, String legalName, String alias) {
  public String displayName() {
    return alias != null && !alias.isBlank() ? alias : legalName;
  }
}
