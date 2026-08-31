package com.smartbox.investory.investment.api.reporting;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

public enum PerformanceAggregation implements ApiWireValue {
  MONTHLY("monthly"),
  QUARTERLY("quarterly"),
  ANNUAL("annual");

  private final String value;

  PerformanceAggregation(String value) {
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
  public static PerformanceAggregation fromWireValue(String value) {
    return ApiEnumParser.parse(value, PerformanceAggregation.class, "aggregation");
  }
}
