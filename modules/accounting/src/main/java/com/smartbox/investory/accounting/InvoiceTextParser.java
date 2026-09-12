package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class InvoiceTextParser {
  private static final Pattern REFERENCE =
      Pattern.compile(
          "(?im)^(?:faktura(?:\u00a0| )?(?:vat)?|invoice|nr(?:\u00a0| )?faktury|numer(?:\u00a0| )?faktury)\\s*[:#-]?\\s*([^\\r\\n]+)");
  private static final Pattern DATE =
      Pattern.compile("(?<!\\d)(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}|\\d{4}-\\d{2}-\\d{2})(?!\\d)");
  private static final Pattern NIP =
      Pattern.compile("((?<!\\d)\\d{3}[- ]?\\d{3}[- ]?\\d{2}[- ]?\\d{2}(?!\\d))");
  private static final Pattern AMOUNT =
      Pattern.compile(
          "(\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}|\\d{1,3}(?:[ .]\\d{3})*(?:,\\d{1,2}|\\.\\d{1,2})|\\d+(?:,\\d{1,2}|\\.\\d{1,2}))");
  private static final Pattern VAT_RATE = Pattern.compile("(\\d{1,2})\\s*%");
  private static final Pattern CORRECTION_REFERENCE = Pattern.compile("(?im)\\b(KOR/[A-Z0-9/_-]+)");
  private static final Pattern LABEL_LINE = Pattern.compile("(?im)^\\s*%s\\s*[:\\-]?\\s*(.+)$");
  private static final Pattern AMOUNT_LINE = Pattern.compile("(?im)^.*%s.*$");
  private static final Pattern ZERO_VAT_MARKER =
      Pattern.compile("(?is)(?:\\bzw\\b|\\bnp\\b|np(?=\\d|\\s|$))");
  private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP", "CZK", "PLN");

  ParseResult parse(String rawText) {
    return parse(null, null, rawText);
  }

  ParseResult parse(DocumentText document, String ownNip) {
    return parse(document, ownNip, document.plainText());
  }

  List<VatSummaryRow> vatSummaryRows(DocumentText document) {
    List<VatSummaryRow> rows = new ArrayList<>();
    for (DocumentText.Page page : document.pages()) {
      for (DocumentText.Line line : page.lines()) {
        Matcher rate = VAT_RATE.matcher(line.text());
        if (!rate.find()) continue;
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher amount = AMOUNT.matcher(line.text());
        while (amount.find()) amounts.add(decimal(amount.group()));
        if (amounts.size() >= 3) {
          int n = amounts.size();
          rows.add(new VatSummaryRow(new BigDecimal(rate.group(1)), amounts.get(n - 3), amounts.get(n - 2), amounts.get(n - 1)));
        }
      }
    }
    return List.copyOf(rows);
  }

  record VatSummaryRow(BigDecimal rate, BigDecimal net, BigDecimal vat, BigDecimal gross) {}

  private ParseResult parse(DocumentText document, String ownNip, String rawText) {
    String text = normalize(rawText);
    if (text.isBlank()) return ParseResult.partial("PDF text is empty");

    List<String> warnings = new ArrayList<>();
    String reference = firstGroup(REFERENCE, text);
    String lower = text.toLowerCase(Locale.ROOT);
    boolean correction = containsAny(lower, "faktura korygująca", "credit note", "correction");
    if (reference == null && correction) reference = firstGroup(CORRECTION_REFERENCE, text);
    LocalDate issueDate = labeledDate(text, "data wystawienia", "issue date", "wystawiono");
    if (issueDate == null) issueDate = firstDate(text);
    LocalDate saleDate = labeledDate(text, "data sprzedaży", "sale date");
    LocalDate dueDate = labeledDate(text, "termin płatności", "due date", "płatność do");
    String seller = labeledLine(text, "sprzedawca", "seller", "wystawca");
    String buyer = labeledLine(text, "nabywca", "buyer", "odbiorca");
    BigDecimal net = labeledAmount(text, "netto", "net amount", "net");
    BigDecimal vat = labeledAmount(text, "vat", "podatek vat", "kwota vat");
    BigDecimal gross = labeledAmount(text, "brutto", "gross amount", "gross");
    InferredTotals inferred = inferTotals(text);
    if (net == null) net = inferred.net();
    if (vat == null) vat = inferred.vat();
    if (gross == null) gross = inferred.gross();
    boolean zeroVat = hasZeroVatMarker(lower);
    if ((zeroVat || correction) && gross != null) {
      if (net == null) net = gross;
      if (vat == null) vat = BigDecimal.ZERO;
    }
    String currency = currency(text);
    String sellerNip = nip(seller, text);
    String buyerNip = nip(buyer, buyer);
    String direction = direction(sellerNip, buyerNip, ownNip, correction);

    addMissingWarnings(warnings, reference, issueDate, seller, text, net, vat, gross);

    if (!warnings.isEmpty()) return new ParseResult(null, ScanStatus.PARTIAL, warnings);
    return new ParseResult(
        new AccountingInvoiceRecognitionService.RecognizedInvoice(
            direction,
            issueDate,
            saleDate,
            dueDate,
            reference,
            seller,
            buyer,
            category(text),
            currency,
            net,
            vat,
            gross,
            "Deterministic text extraction",
            sellerNip,
            buyerNip,
            List.of(
                candidate("reference", reference, "EXPLICIT_LABEL", reference),
                candidate("issueDate", issueDate, "EXPLICIT_LABEL", issueDate),
                candidate("seller", seller, "EXPLICIT_LABEL", seller),
                candidate("net", net, "TABLE_VALUE", net),
                candidate("vat", vat, "TABLE_VALUE", vat),
                candidate("gross", gross, "TABLE_VALUE", gross))),
        ScanStatus.COMPLETE,
        List.of());
  }

  private String direction(String sellerNip, String buyerNip, String ownNip, boolean correction) {
    if (correction) return "CREDIT_NOTE";
    String own = digits(ownNip);
    if (own != null && own.equals(digits(sellerNip))) return "SALES_INVOICE";
    if (own != null && own.equals(digits(buyerNip))) return "PURCHASE_INVOICE";
    return "UNKNOWN";
  }

  private String nip(String preferred, String fallback) {
    String value = firstGroup(NIP, preferred == null ? "" : preferred);
    return value == null ? firstGroup(NIP, fallback) : value;
  }

  private String digits(String value) {
    if (value == null) return null;
    String result = value.replaceAll("\\D", "");
    return result.length() == 10 ? result : null;
  }

  private AccountingInvoiceRecognitionService.FieldCandidate<Object> candidate(
      String field, Object value, String source, Object evidence) {
    return new AccountingInvoiceRecognitionService.FieldCandidate<>(
        value, AccountingInvoiceRecognitionService.ExtractionSource.valueOf(source),
        field + " extracted from invoice text: " + String.valueOf(evidence));
  }

  private String category(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("bp ") || lower.contains("paliwo") || lower.contains("fuel")) {
      return "VEHICLE_FUEL";
    }
    if (lower.contains("księg") || lower.contains("accounting")) return "ACCOUNTING_SERVICE";
    return "OTHER";
  }

  private String currency(String text) {
    String upper = text.toUpperCase(Locale.ROOT);
    for (String code : CURRENCIES) {
      if (upper.contains(code)) return code;
    }
    return "PLN";
  }

  private boolean hasZeroVatMarker(String lower) {
    return lower.contains("odwrotne obciążenie")
        || lower.contains("reverse charge")
        || ZERO_VAT_MARKER.matcher(lower).find()
        || lower.contains("zwolnione z vat")
        || lower.contains("exempt from vat");
  }

  private boolean containsAny(String text, String... values) {
    for (String value : values) {
      if (text.contains(value)) return true;
    }
    return false;
  }

  private void addMissingWarnings(
      List<String> warnings,
      String reference,
      LocalDate issueDate,
      String seller,
      String text,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross) {
    if (reference == null) warnings.add("missing invoice number");
    if (issueDate == null) warnings.add("missing issue date");
    if (firstGroup(NIP, seller == null ? text : seller) == null && firstGroup(NIP, text) == null) {
      warnings.add("missing seller NIP");
    }
    if (net == null) warnings.add("missing net amount");
    if (vat == null) warnings.add("missing VAT amount");
    if (gross == null) warnings.add("missing gross amount");
  }

  private String normalize(String rawText) {
    if (rawText == null) return "";
    return rawText.replace('\u00a0', ' ').trim();
  }

  private String labeledLine(String text, String... labels) {
    for (String label : labels) {
      Matcher matcher =
          Pattern.compile(String.format(LABEL_LINE.pattern(), Pattern.quote(label))).matcher(text);
      if (matcher.find()) return clean(matcher.group(1));
    }
    return null;
  }

  private LocalDate labeledDate(String text, String... labels) {
    String line = labeledLine(text, labels);
    return line == null ? null : parseDate(firstGroup(DATE, line));
  }

  private BigDecimal labeledAmount(String text, String... labels) {
    for (String label : labels) {
      Matcher matcher =
          Pattern.compile(String.format(AMOUNT_LINE.pattern(), Pattern.quote(label))).matcher(text);
      while (matcher.find()) {
        Matcher amount = AMOUNT.matcher(matcher.group());
        BigDecimal last = null;
        while (amount.find()) last = decimal(amount.group());
        if (last != null) return last;
      }
    }
    return null;
  }

  private InferredTotals inferTotals(String text) {
    BigDecimal gross = lastAmountAfter(text, "razem do zapłaty", "total in", "total");
    if (gross == null)
      gross = lastAmountAfterPattern(text, "(?is)razem\\s+do\\s+zap(?:\\s+\\S+)?\\s*:");
    if (gross == null) return new InferredTotals(null, null, null);

    String lower = text.toLowerCase(Locale.ROOT);
    int tableStart = lower.lastIndexOf("nabywca");
    int tableEnd = lower.indexOf("razem do zapłaty", tableStart < 0 ? 0 : tableStart);
    if (tableEnd < 0) return new InferredTotals(null, null, gross);
    String table = text.substring(Math.max(tableStart, 0), tableEnd);
    Matcher rateMatcher = VAT_RATE.matcher(table);
    BigDecimal rate = rateMatcher.find() ? new BigDecimal(rateMatcher.group(1)) : null;
    if (rate == null) return new InferredTotals(null, null, gross);

    List<BigDecimal> amounts = new ArrayList<>();
    Matcher amountMatcher = AMOUNT.matcher(table);
    while (amountMatcher.find()) {
      BigDecimal candidate = decimal(amountMatcher.group());
      if (candidate.compareTo(BigDecimal.ZERO) > 0 && candidate.compareTo(gross) < 0) {
        amounts.add(candidate);
      }
    }
    BigDecimal ratio = rate.movePointLeft(2);
    for (BigDecimal candidate : amounts) {
      BigDecimal calculatedVat = gross.subtract(candidate);
      BigDecimal calculatedRatio = calculatedVat.divide(candidate, 6, RoundingMode.HALF_UP);
      if (calculatedRatio.subtract(ratio).abs().compareTo(new BigDecimal("0.01")) <= 0) {
        return new InferredTotals(candidate, calculatedVat, gross);
      }
    }
    return new InferredTotals(null, null, gross);
  }

  private BigDecimal lastAmountAfter(String text, String... labels) {
    BigDecimal result = null;
    for (String label : labels) {
      Matcher labelMatcher =
          Pattern.compile("(?is)" + Pattern.quote(label) + "\\s*:").matcher(text);
      while (labelMatcher.find()) {
        BigDecimal amount = firstAmount(text.substring(labelMatcher.end()));
        if (amount != null) result = amount;
      }
    }
    return result;
  }

  private BigDecimal lastAmountAfterPattern(String text, String labelPattern) {
    BigDecimal result = null;
    Matcher labelMatcher = Pattern.compile(labelPattern).matcher(text);
    while (labelMatcher.find()) {
      BigDecimal amount = firstAmount(text.substring(labelMatcher.end()));
      if (amount != null) result = amount;
    }
    return result;
  }

  private BigDecimal firstAmount(String text) {
    Matcher matcher = AMOUNT.matcher(text);
    return matcher.find() ? decimal(matcher.group()) : null;
  }

  private LocalDate firstDate(String text) {
    Matcher matcher = DATE.matcher(text);
    return matcher.find() ? parseDate(matcher.group()) : null;
  }

  private LocalDate parseDate(String value) {
    if (value == null) return null;
    if (value.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(value);
    String normalized = value.replace('/', '.');
    for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("d.M.uuuu"))) {
      try {
        return LocalDate.parse(normalized, formatter);
      } catch (DateTimeParseException ignored) {
        // Try the next supported date shape.
      }
    }
    return null;
  }

  private BigDecimal decimal(String value) {
    String normalized = value.replace(" ", "");
    if (normalized.contains(",") && normalized.contains(".")) {
      if (normalized.lastIndexOf('.') > normalized.lastIndexOf(',')) {
        normalized = normalized.replace(",", "");
      } else {
        normalized = normalized.replace(".", "").replace(',', '.');
      }
    } else if (normalized.contains(",")) {
      normalized = normalized.replace(',', '.');
    }
    return new BigDecimal(normalized);
  }

  private String firstGroup(Pattern pattern, String text) {
    if (text == null) return null;
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? clean(matcher.group(1)) : null;
  }

  private String clean(String value) {
    return value == null ? null : value.trim().replaceAll("\\s+", " ");
  }

  record ParseResult(
      AccountingInvoiceRecognitionService.RecognizedInvoice invoice,
      ScanStatus status,
      List<String> warnings) {
    static ParseResult partial(String warning) {
      return new ParseResult(null, ScanStatus.PARTIAL, List.of(warning));
    }
  }

  private record InferredTotals(BigDecimal net, BigDecimal vat, BigDecimal gross) {}
}
