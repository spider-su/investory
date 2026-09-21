package com.smartbox.investory.ryczalt.domain;

public record RuleMatchResult(Kind kind, CounterpartyRule rule) {
  public enum Kind {
    MATCHED,
    NO_MATCH,
    AMBIGUOUS
  }

  public static RuleMatchResult noMatch() {
    return new RuleMatchResult(Kind.NO_MATCH, null);
  }

  public static RuleMatchResult ambiguous() {
    return new RuleMatchResult(Kind.AMBIGUOUS, null);
  }
}
