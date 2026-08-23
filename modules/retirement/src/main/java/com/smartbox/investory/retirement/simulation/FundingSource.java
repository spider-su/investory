package com.smartbox.investory.retirement.simulation;

/** Persisted compatibility values mapped to Retirement's economic funding sources. */
public enum FundingSource {
  CASH,
  BONDS,
  STOCKS;

  public String economicName() {
    return switch (this) {
      case CASH -> "RESERVE";
      case BONDS -> "LONG_TERM";
      case STOCKS -> "INVESTMENT";
    };
  }
}
