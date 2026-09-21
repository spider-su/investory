package com.smartbox.investory.ryczalt.calculation;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/** Stable semantic fingerprint. Database ids, timestamps and collection order are excluded. */
public final class InputFingerprint {
  private InputFingerprint() {}

  public static String ryczalt(RyczaltCalculationInput input, String ruleVersion) {
    StringBuilder canonical = new StringBuilder(ruleVersion);
    canonical.append('|').append(input.socialContributionDeduction().toPlainString());
    canonical.append('|').append(input.healthContributionPaid().toPlainString());
    canonical.append('|').append(input.deductionsAlreadyConsumed().toPlainString());
    input.revenueByRate().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                canonical
                    .append('|')
                    .append(entry.getKey().stripTrailingZeros().toPlainString())
                    .append('=')
                    .append(entry.getValue().stripTrailingZeros().toPlainString()));
    return sha256(canonical.toString());
  }

  public static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte part : digest) result.append(String.format("%02x", part));
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
