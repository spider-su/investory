package com.smartbox.investory.investment.api;

import java.util.Arrays;

/** One strict, case-insensitive parser for public enum wire values. */
public final class ApiEnumParser {
  private ApiEnumParser() {}

  public static <E extends Enum<E> & ApiWireValue> E parse(
      String raw, Class<E> type, String parameter) {
    String normalized = raw == null ? "" : raw.trim();
    return Arrays.stream(type.getEnumConstants())
        .filter(value -> value.wireValue().equalsIgnoreCase(normalized))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Invalid "
                        + parameter
                        + ": "
                        + raw
                        + ". Expected one of "
                        + Arrays.stream(type.getEnumConstants())
                            .map(ApiWireValue::wireValue)
                            .toList()));
  }
}
