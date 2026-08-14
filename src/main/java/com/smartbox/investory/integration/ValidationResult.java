package com.smartbox.investory.integration;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
  public ValidationResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static ValidationResult success() {
    return new ValidationResult(true, List.of());
  }

  public static ValidationResult invalid(String error) {
    return new ValidationResult(false, List.of(error));
  }
}
