package com.smartbox.investory.longterm.api.model;

public enum LongTermAssetType {
  REAL_ESTATE,
  BOND,
  DEPOSIT,
  CASH_RESERVE,
  OTHER;

  public boolean contributesToCalculations() {
    return this != OTHER;
  }
}
