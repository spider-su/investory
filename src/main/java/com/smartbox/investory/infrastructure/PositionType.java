package com.smartbox.investory.infrastructure;

public enum PositionType {
  BUY,
  SELL;

  public static PositionType fromString(String value) {
    if (value == null) {
      return BUY;
    }
    return switch (value.toUpperCase()) {
      case "BUY" -> BUY;
      case "SELL" -> SELL;
      default -> BUY;
    };
  }

  public static PositionParseState parseState(String value) {
    if (value == null) return PositionParseState.UNKNOWN;
    return switch (value.trim().toUpperCase()) {
      case "BUY" -> PositionParseState.BUY;
      case "SELL" -> PositionParseState.SELL;
      case "CLOSED" -> PositionParseState.CLOSED;
      default -> PositionParseState.UNKNOWN;
    };
  }

  public static PositionType fromBrokerSideOrBuy(String value) {
    if (value == null) {
      return BUY;
    }
    String normalized = value.trim().toUpperCase();
    if (normalized.contains("SELL") || normalized.contains("SHORT")) {
      return SELL;
    }
    if (normalized.contains("BUY") || normalized.contains("LONG")) {
      return BUY;
    }
    PositionType parsed = fromString(normalized);
    return parsed == SELL ? SELL : BUY;
  }
}
