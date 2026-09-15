package com.smartbox.investory.accounting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

/** Stable semantic fingerprint for confirmation, independent of Java record toString(). */
public final class AccountingFilingFingerprint {
  private AccountingFilingFingerprint() {}

  public static String sha256(AccountingFilingInput input) {
    StringBuilder value =
        new StringBuilder(input.schemaVersion())
            .append("|RULES=RYCZALT_2026,ZUS_2026,VAT_2026|")
            .append(input.period())
            .append('|')
            .append(input.schemaVersion())
            .append('|')
            .append(input.taxpayer().nip())
            .append('|')
            .append(input.taxpayer().firstName())
            .append('|')
            .append(input.taxpayer().surname())
            .append('|')
            .append(input.taxpayer().dateOfBirth())
            .append('|')
            .append(input.taxpayer().taxOfficeCode())
            .append('|')
            .append(input.taxpayer().hasUop())
            .append('|')
            .append(input.vat().calculatedVat())
            .append('|')
            .append(input.ryczalt().calculatedTax())
            .append('|')
            .append(input.zus().totalZus());
    Stream.concat(input.sales().stream(), input.purchases().stream())
        .sorted(
            Comparator.comparing(
                AccountingFilingInput.FilingDocument::reference,
                Comparator.nullsFirst(String::compareTo)))
        .forEach(
            d ->
                value
                    .append('|')
                    .append(d.reference())
                    .append('|')
                    .append(d.issueDate())
                    .append('|')
                    .append(d.saleDate())
                    .append('|')
                    .append(d.purchaseDate())
                    .append('|')
                    .append(d.netAmount())
                    .append('|')
                    .append(d.vatAmount())
                    .append('|')
                    .append(d.deductibleVat())
                    .append('|')
                    .append(d.vatRate())
                    .append('|')
                    .append(d.treatment())
                    .append('|')
                    .append(d.counterpartyCountry())
                    .append('|')
                    .append(d.counterpartyIdentifier())
                    .append('|')
                    .append(d.counterpartyName())
                    .append('|')
                    .append(d.evidence() == null ? null : d.evidence().type())
                    .append('|')
                    .append(d.evidence() == null ? null : d.evidence().ksefNumber()));
    return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256(byte[] payload) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
      StringBuilder result = new StringBuilder();
      for (byte b : digest) result.append("%02x".formatted(b));
      return result.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
