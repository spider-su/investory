package com.smartbox.investory.investment.api.reporting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

public enum PerformanceMetric implements ApiWireValue {
  RETURN("return"),
  PROFIT("profit");

  private final String value;

  PerformanceMetric(String value) {
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
  public static PerformanceMetric fromWireValue(String value) {
    return ApiEnumParser.parse(value, PerformanceMetric.class, "metric");
  }
}
