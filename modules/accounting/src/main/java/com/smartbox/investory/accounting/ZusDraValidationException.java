package com.smartbox.investory.accounting;

import java.util.List;

/** Raised when a DRA cannot be safely rendered as a filing artifact. */
public final class ZusDraValidationException extends IllegalArgumentException {
  private final List<String> issues;

  public ZusDraValidationException(List<String> issues) {
    super(String.join("; ", issues));
    this.issues = List.copyOf(issues);
  }

  public List<String> issues() {
    return issues;
  }
}
