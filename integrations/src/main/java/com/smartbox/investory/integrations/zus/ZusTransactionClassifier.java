package com.smartbox.investory.integrations.zus;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative classification of normalized bank rows as ZUS payments. */
public final class ZusTransactionClassifier {
  private ZusTransactionClassifier() {}

  public static boolean isZusCounterparty(String counterparty) {
    if (counterparty == null || counterparty.isBlank()) return false;
    String normalized =
        Normalizer.normalize(counterparty, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('Ł', 'L')
            .replace('ł', 'l')
            .toUpperCase(Locale.ROOT);
    return normalized.matches(".*\\bZUS\\b.*")
        || normalized.contains("ZAKLAD UBEZPIECZEN SPOLECZNYCH");
  }
}
