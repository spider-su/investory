package com.example.demo.data;

public enum PositionType {
    BUY,
    SELL,
    CLOSED,
    UNKNOWN;

    public static PositionType fromString(String value) {
        switch (value.toUpperCase()) {
            case "BUY": return BUY;
            case "SELL": return SELL;
            case "CLOSED": return CLOSED;
            default: return UNKNOWN;
        }
    }
}
