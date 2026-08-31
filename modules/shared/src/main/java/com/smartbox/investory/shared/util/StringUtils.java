package com.smartbox.investory.shared.util;

public final class StringUtils {
  private StringUtils() {}

  /**
   * Intentional shared vocabulary for blank checks at module boundaries.
   *
   * <p>Keep this one-line wrapper so callers do not depend on the selected text library and older
   * modules retain a stable import.
   */
  public static boolean isBlank(CharSequence value) {
    return org.apache.commons.lang3.StringUtils.isBlank(value);
  }

  public static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
