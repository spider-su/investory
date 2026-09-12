package com.smartbox.investory.poc.accounting.ksef;

import com.smartbox.investory.accounting.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
import com.smartbox.investory.integrations.ksef.KsefClient.KsefAccess;
import com.smartbox.investory.integrations.ksef.KsefEnvironment;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Controller
public class KsefConnectionController {
  private static final String DEFAULT_TOKEN = "change-me-ksef-token";
  private static final int KSEF_PAGE_SIZE = 250;
  private static final int KSEF_MAX_PAGES = 100;

  private final com.smartbox.investory.integrations.ksef.KsefClient client;
  private final KsefEnvironment environment;
  private final String nip;
  private final String token;
  private final KsefInvoiceXmlParser invoiceParser;
  private final AccountingInvoiceIngestionService invoiceIngestionService;
  private final ObjectMapper objectMapper;
  private final AccountingSourceEvidenceService sourceEvidenceService;

  @Autowired
  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      @Value("${app.ksef.environment:TEST}") KsefEnvironment environment,
      @Value("${app.ksef.nip:}") String nip,
      @Value("${app.ksef.token:}") String token,
      KsefInvoiceXmlParser invoiceParser,
      AccountingInvoiceIngestionService invoiceIngestionService,
      ObjectMapper objectMapper,
      AccountingSourceEvidenceService sourceEvidenceService) {
    this.client = client;
    this.environment = environment;
    this.nip = nip;
    this.token = token;
    this.invoiceParser = invoiceParser;
    this.invoiceIngestionService = invoiceIngestionService;
    this.objectMapper = objectMapper;
    this.sourceEvidenceService = sourceEvidenceService;
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token) {
    this(client, environment, nip, token, null, null, null, null);
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token,
      KsefInvoiceXmlParser parser,
      AccountingInvoiceIngestionService ingestion,
      ObjectMapper objectMapper) {
    this(client, environment, nip, token, parser, ingestion, objectMapper, null);
  }

  @PostMapping("/poc/accounting/ksef/test-connection")
  public String testConnection(
      @RequestParam(required = false) String month, RedirectAttributes redirectAttributes) {
    try {
      validateToken();
      KsefAccess access = client.authenticateWithToken(environment, nip, token);
      if (access.accessToken() == null || access.accessToken().isBlank()) {
        throw new IllegalStateException("KSeF authentication returned no access token");
      }
      redirectAttributes.addFlashAttribute(
          "ksefConnectionMessage", "KSeF authentication succeeded.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "ksefConnectionError", "KSeF connection failed: " + safeMessage(exception));
    }
    return redirectToAccounting(month);
  }

  @PostMapping("/poc/accounting/ksef/read-invoices")
  public String readInvoices(
      @RequestParam(required = false) String month, RedirectAttributes redirectAttributes) {
    try {
      validateToken();
      LocalDate selectedMonth = parseMonth(month);
      KsefAccess access = client.authenticateWithToken(environment, nip, token);
      List<String> pages = new ArrayList<>();
      List<String> ksefNumbers = new ArrayList<>();
      for (int page = 0; page < KSEF_MAX_PAGES; page++) {
        String invoices =
            client.queryIncomingInvoices(
                environment,
                access.accessToken(),
                selectedMonth.atStartOfDay().atOffset(ZoneOffset.UTC),
                selectedMonth.plusMonths(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                page,
                KSEF_PAGE_SIZE);
        pages.add(invoices);
        List<String> pageNumbers = extractKsefNumbers(invoices);
        ksefNumbers.addAll(pageNumbers);
        if (pageNumbers.size() < KSEF_PAGE_SIZE) break;
      }
      ImportResult result =
          importIncomingInvoices(access.accessToken(), selectedMonth, ksefNumbers);
      redirectAttributes.addFlashAttribute("ksefInvoicesJson", mergePages(pages));
      redirectAttributes.addFlashAttribute(
          "ksefConnectionMessage",
          "KSeF metadata read; imported "
              + result.imported()
              + " new invoice(s); skipped "
              + result.skipped()
              + ".");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "ksefInvoiceReadError", "KSeF invoice read failed: " + safeMessage(exception));
    }
    return redirectToAccounting(month);
  }

  private ImportResult importIncomingInvoices(
      String accessToken, LocalDate taxPeriod, List<String> ksefNumbers) {
    if (invoiceParser == null || invoiceIngestionService == null) {
      return new ImportResult(0, 0);
    }
    int imported = 0;
    int skipped = 0;
    for (String ksefNumber : ksefNumbers) {
      long sourceId = 0;
      try {
        if (sourceEvidenceService != null) {
          var existing = sourceEvidenceService.findId(AccountingSourceType.KSEF, ksefNumber);
          if (existing != null
              && existing.isPresent()
              && sourceEvidenceService.status(existing.get()) == AccountingSourceStatus.IMPORTED) {
            skipped++;
            continue;
          }
        }
        String xml = client.downloadInvoice(environment, accessToken, ksefNumber);
        sourceId =
            sourceEvidenceService == null
                ? 0
                : sourceEvidenceService.receiveKsef(
                    ksefNumber, null, xml.getBytes(StandardCharsets.UTF_8));
        if (sourceId != 0
            && sourceEvidenceService.status(sourceId) == AccountingSourceStatus.IMPORTED) {
          skipped++;
          continue;
        }
        var invoice = invoiceParser.parse(xml.getBytes(StandardCharsets.UTF_8));
        if (sourceId != 0)
          sourceEvidenceService.status(sourceId, AccountingSourceStatus.PARSED, null);
        if (invoice.category() == null || invoice.vatDeductionRatio() == null) {
          if (sourceId != 0)
            sourceEvidenceService.status(
                sourceId,
                AccountingSourceStatus.REVIEW_REQUIRED,
                "Tax category or VAT deduction is not proven");
          skipped++;
          continue;
        }
        String supplier = firstNonBlank(invoice.sellerName(), invoice.sellerNip());
        String currency = invoice.currency() == null ? "PLN" : invoice.currency();
        boolean saved =
            invoiceIngestionService.ingest(
                new ReviewedInvoice(
                    taxPeriod,
                    "PURCHASE_INVOICE",
                    invoice.issueDate(),
                    invoice.saleDate(),
                    invoice.reference(),
                    supplier,
                    invoice.category(),
                    currency,
                    invoice.netAmount(),
                    invoice.vatAmount(),
                    invoice.grossAmount(),
                    invoice.vatDeductionRatio(),
                    "KSEF_SOURCE_DOCUMENT",
                    "KSeF " + ksefNumber + "; supplier " + supplier,
                    sourceId == 0 ? null : Long.toString(sourceId)));
        if (saved) imported++;
        if (sourceId != 0)
          sourceEvidenceService.status(sourceId, AccountingSourceStatus.IMPORTED, null);
      } catch (RuntimeException exception) {
        if (sourceId == 0 && sourceEvidenceService != null) {
          try {
            sourceId = sourceEvidenceService.receiveKsef(ksefNumber, null, new byte[0]);
          } catch (RuntimeException ignored) {
            // Preserve the original KSeF failure if the failure marker itself cannot be stored.
          }
        }
        if (sourceId != 0 && sourceEvidenceService != null) {
          sourceEvidenceService.status(
              sourceId, AccountingSourceStatus.FAILED, exception.getMessage());
        }
        skipped++;
      }
    }
    return new ImportResult(imported, skipped);
  }

  private List<String> extractKsefNumbers(String metadataJson) {
    if (objectMapper == null || metadataJson == null || metadataJson.isBlank()) return List.of();
    try {
      JsonNode root = objectMapper.readTree(metadataJson);
      List<String> numbers = new ArrayList<>();
      for (JsonNode invoice : root.path("invoices")) {
        String number = invoice.path("ksefNumber").asString("").trim();
        if (!number.isBlank()) numbers.add(number);
      }
      return numbers;
    } catch (RuntimeException exception) {
      throw new IllegalStateException("KSeF returned invalid invoice metadata", exception);
    }
  }

  private String mergePages(List<String> pages) {
    if (objectMapper == null || pages.size() == 1)
      return pages.isEmpty() ? "{\"invoices\":[]}" : pages.get(0);
    try {
      var merged = objectMapper.createObjectNode();
      var invoices = merged.putArray("invoices");
      for (String page : pages) {
        JsonNode root = objectMapper.readTree(page);
        for (JsonNode invoice : root.path("invoices")) invoices.add(invoice);
      }
      return objectMapper.writeValueAsString(merged);
    } catch (RuntimeException exception) {
      return pages.get(0);
    }
  }

  private record ImportResult(int imported, int skipped) {}

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private void validateToken() {
    if (token == null || token.isBlank() || DEFAULT_TOKEN.equals(token)) {
      throw new IllegalStateException("app.ksef.token is not configured");
    }
    if (nip == null || !nip.matches("\\d{10}")) {
      throw new IllegalStateException("app.ksef.nip must contain 10 digits");
    }
  }

  private String redirectToAccounting(String month) {
    return month != null && month.matches("\\d{4}-\\d{2}")
        ? "redirect:/poc/accounting?month=" + month
        : "redirect:/poc/accounting";
  }

  private LocalDate parseMonth(String month) {
    if (month == null || !month.matches("\\d{4}-\\d{2}")) {
      throw new IllegalArgumentException("A valid accounting month is required");
    }
    return LocalDate.parse(month + "-01");
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
    message = message.replace('\n', ' ').replace('\r', ' ');
    return message.length() > 240 ? message.substring(0, 240) + "..." : message;
  }
}
