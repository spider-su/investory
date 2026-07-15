package com.example.demo.infrastructure;

public enum PositionType {
    BUY,
    SELL,
    CLOSED,
    UNKNOWN;

    public static PositionType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.toUpperCase()) {
            case "BUY" -> BUY;
            case "SELL" -> SELL;
            case "CLOSED" -> CLOSED;
            default -> UNKNOWN;
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
