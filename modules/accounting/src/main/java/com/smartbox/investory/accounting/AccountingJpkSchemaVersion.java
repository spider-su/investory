package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** Selects the monthly JPK_V7M structure required for a filing period. */
public final class AccountingJpkSchemaVersion {
  private static final LocalDate V3_START = LocalDate.of(2026, 2, 1);
  private static final LocalDate V2_START = LocalDate.of(2022, 1, 1);

  private AccountingJpkSchemaVersion() {}

  public static String forPeriod(LocalDate period) {
    if (period.isBefore(V2_START))
      throw new IllegalArgumentException("JPK_V7M periods before 2022 are not supported");
    return period.isBefore(V3_START) ? "JPK_V7M(2)" : "JPK_V7M(3)";
  }

  public static int variant(String schemaVersion) {
    return switch (schemaVersion) {
      case "JPK_V7M(2)" -> 2;
      case "JPK_V7M(3)" -> 3;
      default -> throw new IllegalArgumentException("Unsupported JPK schema: " + schemaVersion);
    };
  }

  public static boolean requiresEvidenceClassification(String schemaVersion) {
    return variant(schemaVersion) == 3;
  }
}
