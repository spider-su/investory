package com.smartbox.investory.ryczalt.checker;

public record CheckIssue(Code code, CheckSeverity severity, String context) {
  public enum Code {
    MISSING_CALCULATION,
    DIRTY_CALCULATION,
    UNSETTLED_OBLIGATION,
    AMBIGUOUS_PAYMENT
  }
}
