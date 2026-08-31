package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.ExpenseProfileStep;
import com.smartbox.investory.retirement.api.model.RetirementFundingSource;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Parses and serializes values submitted by the retirement simulation forms. */
final class SimulationInputParser {
  private SimulationInputParser() {}

  static BigDecimal percent(Map<String, String> fields, String name, BigDecimal fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : value.movePointLeft(2);
  }

  static BigDecimal decimal(Map<String, String> fields, String name) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  static BigDecimal decimalOr(Map<String, String> fields, String name, BigDecimal fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : value;
  }

  static int integer(Map<String, String> fields, String name, int fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : value.intValueExact();
  }

  static boolean booleanValue(Map<String, String> fields, String name, boolean fallback) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? fallback : Boolean.parseBoolean(raw);
  }

  static <T extends Enum<T>> T enumValue(
      Map<String, String> fields, String name, Class<T> type, T fallback) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? fallback : Enum.valueOf(type, raw);
  }

  static List<RetirementFundingSource> parseFundingOrder(String value) {
    if (value == null || value.isBlank()) return SimulationAssumptions.DEFAULT_FUNDING_ORDER;
    try {
      return Arrays.stream(value.split(","))
          .map(String::trim)
          .map(
              token ->
                  switch (token) {
                    case "CASH", "RESERVE" -> RetirementFundingSource.RESERVE;
                    case "BONDS", "LONG_TERM" -> RetirementFundingSource.LONG_TERM;
                    case "STOCKS", "INVESTMENT" -> RetirementFundingSource.INVESTMENT;
                    default ->
                        throw new IllegalArgumentException("Unknown funding source: " + token);
                  })
          .toList();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown funding source", exception);
    }
  }

  static String serializeExpenseProfile(ExpenseProfile profile) {
    return profile.steps().stream()
        .map(step -> step.fromYear() + ":" + step.factor().toPlainString())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  static ExpenseProfile parseExpenseProfile(String value) {
    if (value == null || value.isBlank()) return ExpenseProfile.EMPTY;
    try {
      return new ExpenseProfile(
          Arrays.stream(value.split(";"))
              .map(String::trim)
              .map(
                  entry -> {
                    String[] parts = entry.split(":", -1);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    return new ExpenseProfileStep(
                        Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim()));
                  })
              .toList());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid expense profile", exception);
    }
  }
}
