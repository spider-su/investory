package com.smartbox.investory.ryczalt.domain;

public record Counterparty(
    long id,
    long profileId,
    String taxIdentifier,
    String country,
    String legalName,
    String alias,
    String bankAccount,
    long ruleCount,
    long invoiceCount) {
  public Counterparty(
      long id,
      long profileId,
      String taxIdentifier,
      String country,
      String legalName,
      String alias) {
    this(id, profileId, taxIdentifier, country, legalName, alias, null, 0, 0);
  }

  public String displayName() {
    return alias != null && !alias.isBlank() ? alias : legalName;
  }
}
