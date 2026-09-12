package com.smartbox.investory.accounting;

public record AccountingFilingEvidence(Type type, String ksefNumber) {
  public enum Type { KSEF, OFF, BFK, DI }
}
