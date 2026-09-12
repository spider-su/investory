package com.smartbox.investory.accounting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

/** Stable semantic fingerprint for confirmation, independent of Java record toString(). */
public final class AccountingFilingFingerprint {
  private AccountingFilingFingerprint() {}

  public static String sha256(AccountingFilingInput input) {
    StringBuilder value = new StringBuilder("JPK_V7M(3)|").append(input.period()).append('|')
        .append(input.schemaVersion()).append('|').append(input.taxpayer().nip()).append('|')
        .append(input.taxpayer().firstName()).append('|').append(input.taxpayer().surname()).append('|')
        .append(input.taxpayer().dateOfBirth()).append('|').append(input.taxpayer().taxOfficeCode()).append('|')
        .append(input.vat().calculatedVat()).append('|').append(input.ryczalt().calculatedTax()).append('|')
        .append(input.zus().totalZus());
    Stream.concat(input.sales().stream(), input.purchases().stream())
        .sorted(Comparator.comparing(AccountingFilingInput.FilingDocument::reference, Comparator.nullsFirst(String::compareTo)))
        .forEach(d -> value.append('|').append(d.reference()).append('|').append(d.issueDate()).append('|')
            .append(d.netAmount()).append('|').append(d.vatAmount()).append('|').append(d.deductibleVat())
            .append('|').append(d.counterpartyIdentifier()).append('|')
            .append(d.evidence() == null ? null : d.evidence().type()).append('|')
            .append(d.evidence() == null ? null : d.evidence().ksefNumber()));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte b : digest) result.append("%02x".formatted(b));
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
