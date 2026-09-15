package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Parses the small semicolon-delimited bank export used by the accounting POC. */
public class AccountingBankFileParser {
  public List<ParsedBankTransaction> parse(byte[] payload) {
    String text = new String(payload == null ? new byte[0] : payload, StandardCharsets.UTF_8);
    String[] lines = text.lines().filter(line -> !line.isBlank()).toArray(String[]::new);
    if (lines.length < 2) throw new IllegalArgumentException("Bank export has no transaction rows");
    String delimiter = lines[0].contains(";") ? ";" : ",";
    List<String> header = cells(lines[0], delimiter);
    if (!header.equals(
        List.of(
            "booking_date",
            "related_period",
            "reference",
            "counterparty",
            "currency",
            "amount",
            "note"))) {
      throw new IllegalArgumentException("Unsupported bank export header");
    }
    return Arrays.stream(lines, 1, lines.length).map(line -> row(cells(line, delimiter))).toList();
  }

  private ParsedBankTransaction row(List<String> cells) {
    if (cells.size() != 7)
      throw new IllegalArgumentException("Bank export row must contain 7 columns");
    return new ParsedBankTransaction(
        LocalDate.parse(required(cells.get(0))),
        blank(cells.get(1)) == null ? null : LocalDate.parse(cells.get(1)),
        required(cells.get(2)),
        required(cells.get(3)),
        required(cells.get(4)).toUpperCase(),
        new BigDecimal(required(cells.get(5)).replace(',', '.')),
        blank(cells.get(6)));
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

  public record ParsedBankTransaction(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      BigDecimal amount,
      String note) {}
}
