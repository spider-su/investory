package com.smartbox.investory.investment.imports;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.temporal.TemporalAccessor;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Importer-owned identity for financial source rows.
 *
 * <p>{@code cash_operations.id} and {@code positions.id} are stable SHA-256-derived source
 * identities. They never use a filename, workbook row number, import time, or calculated values.
 * Callers append a deterministic one-based occurrence only for source rows whose complete stable
 * fingerprints are genuinely identical.
 */
public final class BrokerSourceRowIdentity {
  private BrokerSourceRowIdentity() {}

  public static long id(String fingerprint, int occurrence) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  (normalize(fingerprint) + "|occurrence=" + occurrence)
                      .getBytes(StandardCharsets.UTF_8));
      long value = 0L;
      for (int index = 0; index < Long.BYTES; index++) {
        value = (value << 8) | (hash[index] & 0xffL);
      }
      value &= Long.MAX_VALUE;
      return value == 0L ? 1L : value;
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot hash broker source row identity", exception);
    }
  }

  /**
   * Stable identity for one logical broker-row observation.
   *
   * <p>The physical evidence row is allowed to occur in more than one uploaded file. This hash is
   * therefore deliberately independent of file, batch, import time, archive-member, and physical
   * row location. The occurrence distinguishes genuinely repeated identical rows.
   */
  public static String logicalRowSha256(
      Object provider,
      Object sectionName,
      Object sheetName,
      Object sourceRecordId,
      int occurrence,
      String rawText,
      String rawValuesJson) {
    String fingerprint =
        String.join(
            "|",
            part(provider),
            part(sectionName),
            part(sheetName),
            part(sourceRecordId),
            Integer.toString(occurrence),
            part(rawText),
            Objects.toString(rawValuesJson, ""));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(normalize(fingerprint).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot hash broker source row", exception);
    }
  }

  public static String part(Object value) {
    if (value == null) return "";
    if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
    if (value instanceof Number number)
      return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
    if (value instanceof TemporalAccessor) return value.toString();
    return normalize(value.toString());
  }

  public static String normalize(String value) {
    return com.smartbox.investory.shared.util.StringUtils.nullToEmpty(value)
        .trim()
        .replaceAll("\\s+", " ")
        .toUpperCase(Locale.ROOT);
  }
}
