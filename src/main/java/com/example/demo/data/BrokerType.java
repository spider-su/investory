package com.example.demo.data;

import java.util.Arrays;

public enum BrokerType {
    XTB,
    IBKR;

    public static BrokerType fromValue(String value) {
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported broker: " + value));
    }
}

