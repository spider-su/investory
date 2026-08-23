package com.smartbox.investory.shared.util;

public final class StringUtils {
  private StringUtils() {}

  public static boolean isBlank(CharSequence value) {
    return org.apache.commons.lang3.StringUtils.isBlank(value);
  }
}
