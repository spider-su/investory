package com.smartbox.investory.investment.api.importing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

public enum ImportSource implements ApiWireValue {
  MANUAL,
  API,
  TELEGRAM;

  @Override
  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static ImportSource fromWireValue(String value) {
    return ApiEnumParser.parse(value, ImportSource.class, "source");
  }
}
