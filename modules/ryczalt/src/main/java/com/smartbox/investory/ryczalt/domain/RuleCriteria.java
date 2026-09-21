package com.smartbox.investory.ryczalt.domain;

import java.util.Locale;

/** Canonical form for rule criteria. Service keys remain extensible and case-sensitive. */
public final class RuleCriteria {
  private RuleCriteria() {}

  public static String token(String value, String field) {
    if (value == null) return null;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
    if (!normalized.matches("[A-Z0-9][A-Z0-9_:-]*"))
      throw new IllegalArgumentException(field + " has invalid format");
    return normalized;
  }

  public static String serviceKey(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isBlank()) throw new IllegalArgumentException("serviceKey cannot be blank");
    return normalized;
  }
}
