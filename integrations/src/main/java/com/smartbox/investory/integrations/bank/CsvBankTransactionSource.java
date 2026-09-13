package com.smartbox.investory.integrations.bank;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Deterministic offline bank source for the accounting POC and CI. */
public final class CsvBankTransactionSource implements BankTransactionSource {
  private final byte[] payload;
  private final String externalAccountId;

  public CsvBankTransactionSource(byte[] payload, String externalAccountId) {
    this.payload = payload == null ? new byte[0] : payload.clone();
    if (externalAccountId == null || externalAccountId.isBlank())
      throw new IllegalArgumentException("External bank account ID is required");
    this.externalAccountId = externalAccountId;
  }

  @Override
  public BankTransactionPage transactions(BankTransactionQuery query) {
    if (!externalAccountId.equals(query.externalAccountId()))
      throw new IllegalArgumentException("Bank query account does not match CSV account");
    if (query.continuationToken() != null && !query.continuationToken().isBlank())
      throw new IllegalArgumentException("CSV source does not support continuation tokens");
    String text = new String(payload, StandardCharsets.UTF_8);
    String[] lines = text.lines().filter(line -> !line.isBlank()).toArray(String[]::new);
    if (lines.length < 2) throw new IllegalArgumentException("Bank export has no transaction rows");
    String delimiter = lines[0].contains(";") ? ";" : ",";
    if (!cells(lines[0], delimiter)
        .equals(
            List.of(
                "booking_date",
                "related_period",
                "reference",
                "counterparty",
                "currency",
                "amount",
                "note"))) throw new IllegalArgumentException("Unsupported bank export header");
    String payloadHash = sha256(payload);
    List<ExternalBankTransaction> transactions =
        Arrays.stream(lines, 1, lines.length)
            .map(line -> row(cells(line, delimiter), payloadHash))
            .filter(
                transaction ->
                    (query.from() == null || !transaction.bookingDate().isBefore(query.from()))
                        && (query.to() == null || !transaction.bookingDate().isAfter(query.to())))
            .toList();
    return new BankTransactionPage(transactions, null);
  }

  private ExternalBankTransaction row(List<String> cells, String payloadHash) {
    if (cells.size() != 7)
      throw new IllegalArgumentException("Bank export row must contain 7 columns");
    LocalDate bookingDate = LocalDate.parse(required(cells.get(0)));
    LocalDate relatedPeriod = blank(cells.get(1)) == null ? null : LocalDate.parse(cells.get(1));
    String reference = required(cells.get(2));
    String counterparty = blank(cells.get(3)) == null ? "" : cells.get(3);
    String currency = required(cells.get(4)).toUpperCase();
    BigDecimal amount = new BigDecimal(required(cells.get(5)).replace(',', '.'));
    String note = blank(cells.get(6));
    String identity =
        String.join(
            "\u001f",
            BankDataProvider.CSV.name(),
            externalAccountId,
            bookingDate.toString(),
            amount.toPlainString(),
            currency,
            counterparty,
            reference,
            note == null ? "" : note);
    return new ExternalBankTransaction(
        BankDataProvider.CSV,
        externalAccountId,
        sha256(identity.getBytes(StandardCharsets.UTF_8)),
        bookingDate,
        bookingDate,
        relatedPeriod,
        amount,
        currency,
        counterparty,
        null,
        note,
        reference,
        payloadHash);
  }

  private List<String> cells(String line, String delimiter) {
    return Arrays.stream(line.split(java.util.regex.Pattern.quote(delimiter), -1))
        .map(String::trim)
        .toList();
  }

  private String required(String value) {
    if (blank(value) == null)
      throw new IllegalArgumentException("Bank export contains a blank required value");
    return value;
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String sha256(byte[] value) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format("%02x", value));
    return result.toString();
  }
}
