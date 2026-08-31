package com.smartbox.investory.investment.api.importing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

public enum ImportBroker implements ApiWireValue {
  XTB,
  IBKR;

  @Override
  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static ImportBroker fromWireValue(String value) {
    return ApiEnumParser.parse(value, ImportBroker.class, "broker");
  }
}
