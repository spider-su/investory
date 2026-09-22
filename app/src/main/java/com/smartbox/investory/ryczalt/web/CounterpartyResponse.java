package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.Counterparty;

public record CounterpartyResponse(
    long id,
    String legalName,
    String alias,
    String displayName,
    String taxIdentifier,
    String country,
    long ruleCount,
    long invoiceCount) {
  static CounterpartyResponse of(Counterparty c) {
    return new CounterpartyResponse(
        c.id(),
        c.legalName(),
        c.alias(),
        c.displayName(),
        c.taxIdentifier(),
        c.country(),
        c.ruleCount(),
        c.invoiceCount());
  }
}
