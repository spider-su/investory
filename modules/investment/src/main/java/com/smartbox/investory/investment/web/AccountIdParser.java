package com.smartbox.investory.investment.web;

import java.util.Arrays;
import java.util.List;

/** Parses the shared comma-separated account selection query parameter. */
public final class AccountIdParser {
  private AccountIdParser() {}

  public static List<Long> parse(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    try {
      return Arrays.stream(raw.split(",", -1))
          .map(String::trim)
          .map(AccountIdParser::parseOne)
          .distinct()
          .toList();
    } catch (NumberFormatException exception) {
      throw new InvalidAccountSelectionException(
          "accountIds must contain positive integers", exception);
    }
  }

  private static Long parseOne(String value) {
    long id = Long.parseLong(value);
    if (id <= 0) throw new NumberFormatException(value);
    return id;
  }

  public static final class InvalidAccountSelectionException extends IllegalArgumentException {
    public InvalidAccountSelectionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
