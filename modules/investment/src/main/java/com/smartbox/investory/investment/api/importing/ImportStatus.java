package com.smartbox.investory.investment.api.importing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.smartbox.investory.investment.api.ApiEnumParser;
import com.smartbox.investory.investment.api.ApiWireValue;

/** Stable import outcome exposed by the Investment API. */
public enum ImportStatus implements ApiWireValue {
  COMPLETED,
  PARTIAL,
  FAILED,
  NOT_READY;

  @Override
  @JsonValue
  public String wireValue() {
    return name();
  }

  @JsonCreator
  public static ImportStatus fromWireValue(String value) {
    return ApiEnumParser.parse(value, ImportStatus.class, "status");
  }
}
