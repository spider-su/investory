package com.smartbox.investory.ryczalt.domain;

import java.util.List;

public record RequiredInput(
    String field,
    String inputType,
    boolean required,
    List<RequiredInputOption> options,
    String dependsOn,
    List<String> dependsOnValues) {
  public RequiredInput {
    options = options == null ? List.of() : List.copyOf(options);
    dependsOnValues = dependsOnValues == null ? List.of() : List.copyOf(dependsOnValues);
  }

  public record RequiredInputOption(String value, String labelKey) {}
}
