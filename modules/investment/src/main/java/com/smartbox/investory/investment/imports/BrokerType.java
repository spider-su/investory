package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.api.importing.ImportBroker;
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

  public static BrokerType fromApi(ImportBroker broker) {
    return switch (broker) {
      case XTB -> XTB;
      case IBKR -> IBKR;
    };
  }
}
