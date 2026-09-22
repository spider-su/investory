package com.smartbox.investory.ryczalt.application.port;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Deterministic native adapter for text PDFs and the repository's structured invoice fixture
 * format.
 */
@Component
public class NativeInvoiceRecognitionAdapter implements InvoiceRecognitionPort {
  private static final Pattern PDF = Pattern.compile("%PDF", Pattern.LITERAL);

  @Override
  public RecognizedInvoice recognize(String filename, String contentType, byte[] content) {
    if (content == null || content.length == 0)
      throw new IllegalArgumentException("Invoice file is empty");
    String text = extractText(content, contentType);
    return new RecognizedInvoice(
        value(text, "DOCUMENT_TYPE", "INVOICE"),
        date(text, "ISSUE_DATE"),
        date(text, "SALE_DATE"),
        date(text, "DUE_DATE"),
        required(text, "REFERENCE"),
        party(text, "SELLER"),
        party(text, "BUYER"),
        value(text, "CURRENCY", "PLN").toUpperCase(Locale.ROOT),
        money(text, "NET_AMOUNT"),
        money(text, "VAT_AMOUNT"),
        money(text, "GROSS_AMOUNT"),
        value(text, "DESCRIPTION", null),
        value(text, "SOURCE_METADATA", "{}"),
        BigDecimal.ONE,
        value(text, "SERVICE_KEY", null));
  }

  private static String extractText(byte[] content, String contentType) {
    if (contentType != null && contentType.equalsIgnoreCase("application/pdf")
        || PDF.matcher(
                new String(content, 0, Math.min(content.length, 4), StandardCharsets.ISO_8859_1))
            .find()) {
      try (var document = Loader.loadPDF(content)) {
        return new PDFTextStripper().getText(document);
      } catch (Exception exception) {
        throw new IllegalArgumentException("Invoice PDF text extraction failed", exception);
      }
    }
    return new String(content, StandardCharsets.UTF_8);
  }

  private static Party party(String text, String key) {
    String name = value(text, key, null);
    String tax = value(text, key + "_TAX_IDENTIFIER", null);
    String country = value(text, key + "_COUNTRY", "PL");
    return name == null && tax == null ? null : new Party(name, tax, country);
  }

  private static LocalDate date(String text, String key) {
    String value = value(text, key, null);
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(key + " must use YYYY-MM-DD", exception);
    }
  }

  private static BigDecimal money(String text, String key) {
    return new BigDecimal(required(text, key).replace(" ", "").replace(',', '.'));
  }

  private static String required(String text, String key) {
    String value = value(text, key, null);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing invoice field: " + key);
    return value;
  }

  private static String value(String text, String key, String fallback) {
    for (String line : text.split("\\R")) {
      int separator = Math.max(line.indexOf(':'), line.indexOf('='));
      if (separator < 0) continue;
      if (line.substring(0, separator).trim().equalsIgnoreCase(key))
        return line.substring(separator + 1).trim();
    }
    return fallback;
  }
}
