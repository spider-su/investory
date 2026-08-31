package com.smartbox.investory.investment.api.reporting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

public enum PerformanceStyle implements ApiWireValue {
  LINE("line"),
  BARS("bars");

  private final String value;

  PerformanceStyle(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String wireValue() {
    return value;
  }

  public String value() {
    return value;
  }

  @JsonCreator
  public static PerformanceStyle fromWireValue(String value) {
    return ApiEnumParser.parse(value, PerformanceStyle.class, "style");
  }
}
