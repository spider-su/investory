package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService.RecognizedInvoice;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Opt-in smoke/integration probe for historical invoice recognition.
 *
 * <p>Point it at a local directory with:
 *
 * <pre>
 * mvn -pl app -Dtest=AccountingInvoiceFolderRecognitionIT \
 *   -Daccounting.invoice.it.folder=/path/to/invoices \
 *   -Dapp.openai.enabled=true \
 *   -Dapp.openai.api-key=$OPENAI_API_KEY test
 * </pre>
 *
 * <p>The test scans PDF/JPG/PNG/WEBP files recursively, tries every document, prints an OK/NOK line
 * per file, writes a Markdown report under {@code target/accounting-invoice-recognition-report.md},
 * and only fails after all files have been attempted.
 */
@Tag("external-ai")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountingInvoiceFolderRecognitionIT extends AccountingDatabaseTest {

  private static final String FOLDER_PROPERTY = "accounting.invoice.it.folder";
  private static final String MAX_FILES_PROPERTY = "accounting.invoice.it.max-files";
  private static final Path REPORT = Path.of("target/accounting-invoice-recognition-report.md");
  private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.02");

  @Autowired private AccountingInvoiceRecognitionService recognitionService;

  @Test
  void recognizesEveryHistoricalInvoiceAndCollectsFailures() throws IOException {
    String configuredFolder = System.getProperty(FOLDER_PROPERTY);
    Assumptions.assumeTrue(
        configuredFolder != null && !configuredFolder.isBlank(),
        () -> "Skipped: set -D" + FOLDER_PROPERTY + "=/path/to/invoices to run this probe");

    Path folder = Path.of(configuredFolder).toAbsolutePath().normalize();
    assertThat(folder)
        .as("Configured invoice folder must exist and be a directory: %s", folder)
        .isDirectory();

    int maxFiles = Integer.getInteger(MAX_FILES_PROPERTY, Integer.MAX_VALUE);
    List<Path> files = discoverInvoices(folder, maxFiles);
    assertThat(files).as("No supported invoice files found under %s", folder).isNotEmpty();

    List<Result> results = new ArrayList<>();
    for (Path file : files) {
      results.add(recognizeOne(folder, file));
    }

    writeReport(folder, results);
    printSummary(results);

    List<Result> failures = results.stream().filter(result -> !result.ok()).toList();
    assertThat(failures)
        .as(
            "Invoice recognition had %d NOK result(s). See %s for the collected diagnostics.",
            failures.size(), REPORT.toAbsolutePath())
        .isEmpty();
  }

  private Result recognizeOne(Path root, Path file) {
    String relative = root.relativize(file).toString();
    try {
      byte[] bytes = Files.readAllBytes(file);
      RecognizedInvoice invoice =
          recognitionService.recognize(file.getFileName().toString(), contentType(file), bytes);
      List<String> issues = validate(invoice);
      boolean ok = issues.isEmpty();
      String debug = ok ? summary(invoice) : String.join("; ", issues) + " | " + summary(invoice);
      printResult(relative, ok, debug);
      return new Result(relative, ok, invoice, debug);
    } catch (Exception exception) {
      String debug = shortException(exception);
      printResult(relative, false, debug);
      return new Result(relative, false, null, debug);
    }
  }

  private List<String> validate(RecognizedInvoice invoice) {
    List<String> issues = new ArrayList<>();
    if (invoice == null) {
      issues.add("recognizer returned null");
      return issues;
    }
    if (blank(invoice.documentType()) || "UNKNOWN".equals(invoice.documentType())) {
      issues.add("documentType missing/UNKNOWN");
    }
    if (invoice.issueDate() == null) issues.add("issueDate missing");
    if (blank(invoice.reference())) issues.add("reference missing");
    if (blank(invoice.seller())) issues.add("seller missing");
    if (blank(invoice.currency())) issues.add("currency missing");
    if (invoice.netAmount() == null) issues.add("netAmount missing");
    if (invoice.vatAmount() == null) issues.add("vatAmount missing");
    if (invoice.grossAmount() == null) issues.add("grossAmount missing");

    if ("SALES_INVOICE".equals(invoice.documentType()) && blank(invoice.buyer())) {
      issues.add("buyer missing for SALES_INVOICE");
    }

    if (invoice.netAmount() != null
        && invoice.vatAmount() != null
        && invoice.grossAmount() != null) {
      BigDecimal expectedGross = invoice.netAmount().add(invoice.vatAmount());
      BigDecimal delta = expectedGross.subtract(invoice.grossAmount()).abs();
      if (delta.compareTo(MONEY_TOLERANCE) > 0) {
        issues.add(
            "net+VAT != gross (delta="
                + delta.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + ")");
      }
    }
    return issues;
  }

  private List<Path> discoverInvoices(Path folder, int maxFiles) throws IOException {
    try (Stream<Path> stream = Files.walk(folder)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(this::supported)
          .sorted(Comparator.comparing(Path::toString))
          .limit(maxFiles)
          .toList();
    }
  }

  private boolean supported(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".pdf")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".png")
        || name.endsWith(".webp");
  }

  private String contentType(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".pdf")) return "application/pdf";
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".webp")) return "image/webp";
    return "image/jpeg";
  }

  private void writeReport(Path folder, List<Result> results) throws IOException {
    Files.createDirectories(REPORT.getParent());
    long okCount = results.stream().filter(Result::ok).count();
    StringBuilder report = new StringBuilder();
    report.append("# Accounting invoice recognition report\n\n");
    report.append("Folder: `").append(escape(folder.toString())).append("`\n\n");
    report
        .append("Result: **")
        .append(okCount)
        .append(" OK / ")
        .append(results.size() - okCount)
        .append(" NOK / ")
        .append(results.size())
        .append(" total**\n\n");
    report.append("| Status | Filename | Extracted summary | Failure reason |\n");
    report.append("|---|---|---|---|\n");
    for (Result result : results) {
      RecognizedInvoice invoice = result.invoice();
      report
          .append("| ")
          .append(result.ok() ? "OK" : "NOK")
          .append(" | `")
          .append(escape(result.file()))
          .append("` | ")
          .append(escape(summary(invoice)))
          .append(" | ")
          .append(escape(failureReason(result)))
          .append(" |\n");
    }
    Files.writeString(
        REPORT,
        report.toString(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void printSummary(List<Result> results) {
    long ok = results.stream().filter(Result::ok).count();
    long nok = results.size() - ok;
    System.out.printf(
        "%nInvoice recognition summary: OK=%d NOK=%d TOTAL=%d%nReport: %s%n",
        ok, nok, results.size(), REPORT.toAbsolutePath());
  }

  private void printResult(String file, boolean ok, String debug) {
    System.out.printf("INVOICE_PARSE %-3s | %s | %s%n", ok ? "OK" : "NOK", file, debug);
  }

  private String summary(RecognizedInvoice invoice) {
    if (invoice == null) return "no parsed invoice";
    return "type="
        + value(invoice.documentType())
        + ", issueDate="
        + value(invoice.issueDate() == null ? null : invoice.issueDate().toString())
        + ", saleDate="
        + value(invoice.saleDate() == null ? null : invoice.saleDate().toString())
        + ", dueDate="
        + value(invoice.dueDate() == null ? null : invoice.dueDate().toString())
        + ", ref="
        + value(invoice.reference())
        + ", seller="
        + value(invoice.seller())
        + ", buyer="
        + value(invoice.buyer())
        + ", category="
        + value(invoice.category())
        + ", currency="
        + value(invoice.currency())
        + ", netAmount="
        + value(invoice.netAmount() == null ? null : invoice.netAmount().toPlainString())
        + ", vatAmount="
        + value(invoice.vatAmount() == null ? null : invoice.vatAmount().toPlainString())
        + ", gross="
        + value(invoice.grossAmount() == null ? null : invoice.grossAmount().toPlainString())
        + ", note="
        + value(invoice.note());
  }

  private String failureReason(Result result) {
    if (result.ok()) return "—";
    int summarySeparator = result.debug().indexOf(" | ");
    return summarySeparator < 0 ? result.debug() : result.debug().substring(0, summarySeparator);
  }

  private String shortException(Exception exception) {
    Throwable root = exception;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String message = root.getMessage();
    if (message == null || message.isBlank()) message = exception.getMessage();
    if (message == null || message.isBlank()) message = "no message";
    message = message.replace('\n', ' ').replace('\r', ' ').trim();
    if (message.length() > 240) message = message.substring(0, 240) + "...";
    return root.getClass().getSimpleName() + ": " + message;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String value(String value) {
    return blank(value) ? "—" : escape(value);
  }

  private String escape(String value) {
    if (value == null) return "—";
    return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
  }

  private record Result(String file, boolean ok, RecognizedInvoice invoice, String debug) {}
}
