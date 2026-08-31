package com.smartbox.investory.investment.api.portfolio;

/** Canonical asset taxonomy published to portfolio consumers. */
public enum BrokerageAssetType {
  EQUITY,
  ETF,
  FUND,
  REIT,
  INDEX,
  CRYPTOCURRENCY,
  COMMODITY,
  BOND,
  CASH,
  OTHER;

  public static BrokerageAssetType from(String value) {
    if (value == null) return OTHER;
    try {
      return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return OTHER;
    }
  }

  /** Returns whether a persisted classification is part of the canonical taxonomy. */
  public static boolean isKnown(String value) {
    if (value == null || value.isBlank()) return false;
    try {
      valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
