package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.ksef.KsefClient;
import com.smartbox.investory.integrations.ksef.KsefEnvironment;
import com.smartbox.investory.poc.accounting.ksef.KsefInvoiceXmlParser;
import com.smartbox.investory.poc.accounting.ksef.KsefInvoiceXmlParser.ParsedKsefInvoice;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "accounting.ksef.it.enabled", matches = "true")
class AccountingKsefImportIT {

  private static final int PAGE_SIZE = 100;
  private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.02");

  @Test
  void downloadsAndParsesKsefInvoicesProducingOkNokReport() throws Exception {
    String baseUrl = KsefEnvironment.TEST.baseUrl();
    String ksefToken = requiredSecret("app.ksef.token", "INVESTORY_KSEF_TOKEN");
    String nip = property("app.ksef.nip", "8133703437");
    LocalDate from = LocalDate.parse(property("accounting.ksef.it.from", "2026-01-01"));
    LocalDate to = LocalDate.parse(property("accounting.ksef.it.to", "2026-08-31"));
    List<String> subjectTypes =
        split(property("accounting.ksef.it.subject-types", "Subject1,Subject2"));
    int maxInvoices = Integer.parseInt(property("accounting.ksef.it.max-invoices", "0"));

    ObjectMapper objectMapper = new ObjectMapper();
    ApplicationTime applicationTime = org.mockito.Mockito.mock(ApplicationTime.class);
    org.mockito.Mockito.when(applicationTime.now()).thenReturn(Instant.now());
    KsefClient client = new KsefClient(objectMapper, applicationTime);
    String accessToken =
        client.authenticateWithToken(KsefEnvironment.TEST, nip, ksefToken).accessToken();
    assertThat(accessToken).isNotBlank();
    KsefInvoiceXmlParser parser = new KsefInvoiceXmlParser();

    Map<String, InvoiceMetadata> metadataByKsefNumber = new LinkedHashMap<>();
    for (String subjectType : subjectTypes) {
      for (DateWindow window : windows(from, to)) {
        int pageOffset = 0;
        while (true) {
          List<InvoiceMetadata> page =
              metadataPage(
                  objectMapper,
                  client.queryInvoices(
                      KsefEnvironment.TEST,
                      accessToken,
                      subjectType,
                      window.from().atStartOfDay().atOffset(ZoneOffset.UTC),
                      window.toExclusive().atStartOfDay().atOffset(ZoneOffset.UTC),
                      pageOffset,
                      PAGE_SIZE));
          for (InvoiceMetadata metadata : page) {
            if (metadata.ksefNumber() != null) {
              metadataByKsefNumber.putIfAbsent(metadata.ksefNumber(), metadata);
            }
          }
          if (page.size() < PAGE_SIZE) break;
          pageOffset++;
        }
      }
    }

    List<InvoiceMetadata> selected = new ArrayList<>(metadataByKsefNumber.values());
    if (maxInvoices > 0 && selected.size() > maxInvoices) {
      selected = selected.subList(0, maxInvoices);
    }

    List<Result> results = new ArrayList<>();
    for (InvoiceMetadata metadata : selected) {
      try {
        byte[] xml =
            client
                .downloadInvoice(KsefEnvironment.TEST, accessToken, metadata.ksefNumber())
                .getBytes(StandardCharsets.UTF_8);
        ParsedKsefInvoice invoice = parser.parse(xml);
        List<String> issues = validate(metadata, invoice);
        Result result =
            new Result(
                issues.isEmpty() ? "OK" : "NOK",
                metadata.ksefNumber(),
                invoice.reference(),
                invoice.issueDate(),
                invoice.currency(),
                invoice.grossAmount(),
                issues.isEmpty() ? "parsed" : String.join("; ", issues));
        results.add(result);
        print(result);
      } catch (RuntimeException exception) {
        Result result =
            new Result(
                "NOK",
                metadata.ksefNumber(),
                metadata.invoiceNumber(),
                metadata.issueDate() == null ? null : metadata.issueDate().toLocalDate(),
                metadata.currency(),
                null,
                shortError(exception));
        results.add(result);
        print(result);
      }
    }

    Path report = Path.of("target", "accounting-ksef-import-report.md");
    Files.createDirectories(report.getParent());
    Files.writeString(
        report, report(results, baseUrl, from, to, subjectTypes), StandardCharsets.UTF_8);

    long nok = results.stream().filter(result -> "NOK".equals(result.status())).count();
    System.out.printf(
        "KSEF summary: queried=%d tested=%d OK=%d NOK=%d report=%s%n",
        metadataByKsefNumber.size(),
        results.size(),
        results.size() - nok,
        nok,
        report.toAbsolutePath());

    if (results.isEmpty()) {
      System.out.printf(
          "KSEF note: TEST account returned no invoices for %s..%s (%s); authentication and metadata query succeeded.%n",
          from, to, subjectTypes);
    }
    assertThat(nok)
        .as("KSeF import has NOK invoices; inspect %s", report.toAbsolutePath())
        .isZero();
  }

  private List<String> validate(InvoiceMetadata metadata, ParsedKsefInvoice invoice) {
    List<String> issues = new ArrayList<>();
    if (blank(invoice.reference())) issues.add("reference missing");
    if (invoice.issueDate() == null) issues.add("issueDate missing");
    if (blank(invoice.sellerNip()) && blank(invoice.sellerName())) issues.add("seller missing");
    if (blank(invoice.buyerNip()) && blank(invoice.buyerName())) issues.add("buyer missing");
    if (blank(invoice.currency())) issues.add("currency missing");
    if (invoice.grossAmount() == null) issues.add("gross missing");

    if (invoice.netAmount() != null
        && invoice.vatAmount() != null
        && invoice.grossAmount() != null) {
      BigDecimal difference =
          invoice.netAmount().add(invoice.vatAmount()).subtract(invoice.grossAmount()).abs();
      if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
        issues.add("net+VAT differs from gross by " + difference);
      }
    }

    if (!blank(metadata.invoiceNumber())
        && !blank(invoice.reference())
        && !metadata.invoiceNumber().equals(invoice.reference())) {
      issues.add(
          "reference differs from metadata: "
              + metadata.invoiceNumber()
              + " != "
              + invoice.reference());
    }
    if (metadata.issueDate() != null
        && invoice.issueDate() != null
        && !metadata.issueDate().toLocalDate().equals(invoice.issueDate())) {
      issues.add(
          "issueDate differs from metadata: "
              + metadata.issueDate().toLocalDate()
              + " != "
              + invoice.issueDate());
    }
    return issues;
  }

  private List<InvoiceMetadata> metadataPage(ObjectMapper objectMapper, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      List<InvoiceMetadata> result = new ArrayList<>();
      for (JsonNode node : root.path("invoices")) {
        result.add(
            new InvoiceMetadata(
                text(node, "ksefNumber"),
                text(node, "invoiceNumber"),
                parseOffsetDateTime(text(node, "issueDate")),
                text(node, "currency")));
      }
      return result;
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Invalid KSeF metadata response", exception);
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString(null);
  }

  private OffsetDateTime parseOffsetDateTime(String value) {
    return value == null ? null : OffsetDateTime.parse(value);
  }

  private List<DateWindow> windows(LocalDate from, LocalDate toInclusive) {
    if (toInclusive.isBefore(from)) {
      throw new IllegalArgumentException("accounting.ksef.it.to must not be before from");
    }
    List<DateWindow> result = new ArrayList<>();
    LocalDate cursor = from;
    LocalDate endExclusive = toInclusive.plusDays(1);
    while (cursor.isBefore(endExclusive)) {
      // KSeF metadata dateRange is limited to at most three months. Use two-month chunks
      // to remain safely below the limit regardless of month length and DST/offset details.
      LocalDate next = cursor.plusMonths(2);
      if (next.isAfter(endExclusive)) next = endExclusive;
      result.add(new DateWindow(cursor, next));
      cursor = next;
    }
    return result;
  }

  private String report(
      List<Result> results,
      String baseUrl,
      LocalDate from,
      LocalDate to,
      List<String> subjectTypes) {
    StringBuilder out = new StringBuilder();
    out.append("# KSeF import recognition report\n\n");
    out.append("- Environment: `").append(baseUrl).append("`\n");
    out.append("- Range: `").append(from).append("` .. `").append(to).append("`\n");
    out.append("- Subject types: `").append(String.join(",", subjectTypes)).append("`\n\n");
    out.append("| Status | KSeF number | Invoice | Issue date | Currency | Gross | Debug |\n");
    out.append("|---|---|---|---|---|---:|---|\n");
    for (Result result : results) {
      out.append("| ")
          .append(result.status())
          .append(" | ")
          .append(cell(result.ksefNumber()))
          .append(" | ")
          .append(cell(result.reference()))
          .append(" | ")
          .append(result.issueDate() == null ? "" : result.issueDate())
          .append(" | ")
          .append(cell(result.currency()))
          .append(" | ")
          .append(result.gross() == null ? "" : result.gross())
          .append(" | ")
          .append(cell(result.debug()))
          .append(" |\n");
    }
    return out.toString();
  }

  private void print(Result result) {
    System.out.printf(
        "KSEF %-3s | %s | ref=%s date=%s %s %s | %s%n",
        result.status(),
        safe(result.ksefNumber()),
        safe(result.reference()),
        result.issueDate() == null ? "—" : result.issueDate(),
        safe(result.currency()),
        result.gross() == null ? "—" : result.gross(),
        result.debug());
  }

  private String requiredSecret(String property, String environment) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) value = System.getenv(environment);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "KSeF IT requires -D" + property + "=... or environment variable " + environment);
    }
    return value;
  }

  private String property(String name, String fallback) {
    return System.getProperty(name, fallback);
  }

  private List<String> split(String value) {
    return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String shortError(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) current = current.getCause();
    String message = current.getMessage();
    if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
    return message.length() > 300 ? message.substring(0, 300) + "..." : message;
  }

  private String cell(Object value) {
    return value == null ? "" : value.toString().replace("|", "\\|").replace("\n", " ");
  }

  private String safe(Object value) {
    return value == null ? "—" : value.toString();
  }

  private record DateWindow(LocalDate from, LocalDate toExclusive) {}

  private record InvoiceMetadata(
      String ksefNumber, String invoiceNumber, OffsetDateTime issueDate, String currency) {}

  private record Result(
      String status,
      String ksefNumber,
      String reference,
      LocalDate issueDate,
      String currency,
      BigDecimal gross,
      String debug) {}
}
