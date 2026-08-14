package com.smartbox.investory.services.reconciliation;

public enum ReconciliationCheckpoint {
  C0("files -> import history"),
  C1("files -> cash ledger"),
  C2("ledger -> positions"),
  C3("prices + FX"),
  C4("account_daily"),
  C5("reporting layers"),
  C6("dashboard"),
  C7("secondary adapters");

  private final String displayName;

  ReconciliationCheckpoint(String displayName) {
    this.displayName = displayName;
  }

  public String code() {
    return name();
  }

  public String displayName() {
    return displayName;
  }
}
