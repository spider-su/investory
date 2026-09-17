package com.smartbox.investory.integrations.bank;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic offline bank source for the accounting POC and CI. */
public final class CsvBankTransactionSource implements BankTransactionSource {
  private static final Pattern OBLIGATION_PERIOD = Pattern.compile("(?:OKR/|^)(\\d{2})M(\\d{2})");
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
    String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
    if (firstLine.isBlank())
      throw new IllegalArgumentException("Bank export has no transaction rows");
    char delimiter = firstLine.contains(";") ? ';' : ',';
    List<List<String>> rows = rows(text, delimiter);
    if (rows.size() < 2) throw new IllegalArgumentException("Bank export has no transaction rows");
    if (isPekaoHeader(rows.get(0))) {
      return pekaoTransactions(rows, query);
    }
    if (!rows.get(0)
        .equals(
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
    String payloadHash = sha256(payload);
    List<ExternalBankTransaction> transactions =
        rows.stream()
            .skip(1)
            .map(values -> row(values, payloadHash))
            .filter(
                transaction ->
                    (query.from() == null || !transaction.bookingDate().isBefore(query.from()))
                        && (query.to() == null || !transaction.bookingDate().isAfter(query.to())))
            .toList();
    return new BankTransactionPage(transactions, null);
  }

  private boolean isPekaoHeader(List<String> header) {
    return header.size() >= 11
        && header.get(0).equals("Data księgowania")
        && header.get(1).equals("Data waluty")
        && header.get(2).equals("Nadawca / Odbiorca")
        && header.get(6).equals("Tytułem")
        && header.get(7).equals("Kwota operacji")
        && header.get(8).equals("Waluta")
        && header.get(9).equals("Numer referencyjny");
  }

  private BankTransactionPage pekaoTransactions(
      List<List<String>> rows, BankTransactionQuery query) {
    String accountId = pekaoAccount(rows.subList(1, rows.size()));
    List<ExternalBankTransaction> transactions =
        rows.stream()
            .skip(1)
            .map(row -> pekaoRow(row, accountId))
            .filter(
                transaction ->
                    (query.from() == null || !transaction.bookingDate().isBefore(query.from()))
                        && (query.to() == null || !transaction.bookingDate().isAfter(query.to())))
            .toList();
    return new BankTransactionPage(transactions, null);
  }

  private String pekaoAccount(List<List<String>> rows) {
    Map<String, Integer> frequency = new HashMap<>();
    for (List<String> row : rows) {
      if (row.size() < 6)
        throw new IllegalArgumentException("Pekao export row must contain 11 or 12 columns");
      for (int index : List.of(4, 5)) {
        String account = cleanAccount(row.get(index));
        if (!account.isBlank()) frequency.merge(account, 1, Integer::sum);
      }
    }
    return frequency.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElseThrow(() -> new IllegalArgumentException("Pekao export has no account number"));
  }

  private ExternalBankTransaction pekaoRow(List<String> cells, String accountId) {
    if (cells.size() < 11)
      throw new IllegalArgumentException("Pekao export row must contain 11 or 12 columns");
    LocalDate bookingDate =
        LocalDate.parse(
            required(cells.get(0)), java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    LocalDate valueDate =
        LocalDate.parse(
            required(cells.get(1)), java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    String counterparty = blank(cells.get(2)) == null ? "" : cells.get(2);
    String note = blank(cells.get(6));
    String currency = required(cells.get(8)).toUpperCase();
    BigDecimal amount = pekaoAmount(cells.get(7));
    String reference = cleanValue(required(cells.get(9)));
    LocalDate relatedPeriod = obligationPeriod(note);
    String identity =
        String.join(
            "\u001f",
            BankDataProvider.CSV.name(),
            accountId,
            bookingDate.toString(),
            amount.toPlainString(),
            currency,
            reference,
            note == null ? "" : note);
    return new ExternalBankTransaction(
        BankDataProvider.CSV,
        accountId,
        sha256(identity.getBytes(StandardCharsets.UTF_8)),
        bookingDate,
        valueDate,
        relatedPeriod,
        amount,
        currency,
        counterparty,
        null,
        note,
        reference,
        sha256(String.join(";", cells).getBytes(StandardCharsets.UTF_8)));
  }

  private BigDecimal pekaoAmount(String value) {
    String normalized = required(value).replace(" ", "").replace(" ", "").replace(',', '.');
    return new BigDecimal(normalized);
  }

  private LocalDate obligationPeriod(String note) {
    if (note == null) return null;
    Matcher matcher = OBLIGATION_PERIOD.matcher(note);
    if (!matcher.find()) return null;
    return LocalDate.of(
        2000 + Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), 1);
  }

  private String cleanAccount(String value) {
    return value == null ? "" : value.replace("'", "").replaceAll("\\s+", "").trim();
  }

  private String cleanValue(String value) {
    String trimmed = value == null ? "" : value.trim();
    return trimmed.startsWith("'") ? trimmed.substring(1) : trimmed;
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

  private List<List<String>> rows(String text, char delimiter) {
    try (CSVReader reader =
        new CSVReaderBuilder(new StringReader(text))
            .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
            .build()) {
      return reader.readAll().stream()
          .map(values -> Arrays.stream(values).map(String::trim).toList())
          .filter(values -> values.stream().anyMatch(value -> !value.isBlank()))
          .map(
              values -> {
                if (!values.isEmpty()) values = new java.util.ArrayList<>(values);
                if (!values.isEmpty()) values.set(0, values.get(0).replaceFirst("^\\uFEFF", ""));
                return values;
              })
          .toList();
    } catch (IOException | CsvException exception) {
      throw new IllegalArgumentException("Bank export CSV could not be read", exception);
    }
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
