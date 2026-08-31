package com.smartbox.investory.investment.api.reporting.model;

public enum ReconciliationCheckpoint {
  C0("files -> import history"),
  C1("files -> cash ledger"),
  C2("ledger -> positions"),
  C3("prices + FX"),
  C4("account_daily"),
  C5("reporting layers"),
  C6("dashboard"),
  C7("secondary adapters (Yahoo export)");

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
