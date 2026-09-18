package com.smartbox.investory.poc.accounting.ksef;

import com.smartbox.investory.accounting.AccountingDateRules;
import com.smartbox.investory.accounting.AccountingFilingEvidence;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
import com.smartbox.investory.accounting.api.AccountingKsefSyncPort;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import com.smartbox.investory.accounting.staging.AccountingStagingPromotionService;
import com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService;
import com.smartbox.investory.integrations.ksef.KsefClient.KsefAccess;
import com.smartbox.investory.integrations.ksef.KsefEnvironment;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Controller
public class KsefConnectionController implements AccountingKsefSyncPort {
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
  private final AccountingPocRepository canonicalRepository;
  @Autowired private AccountingStagingAcquisitionService stagingAcquisition;
  @Autowired private AccountingStagingReconciliationService stagingReconciliation;
  @Autowired private AccountingStagingPromotionService stagingPromotion;

  @Autowired
  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      @Value("${app.ksef.environment:TEST}") String environment,
      @Value("${app.ksef.nip:}") String nip,
      @Value("${app.ksef.token:}") String token,
      KsefInvoiceXmlParser invoiceParser,
      AccountingInvoiceIngestionService invoiceIngestionService,
      ObjectMapper objectMapper,
      AccountingSourceEvidenceService sourceEvidenceService,
      AccountingPocRepository canonicalRepository) {
    this.client = client;
    this.environment = KsefEnvironment.parse(environment);
    this.nip = nip;
    this.token = token;
    this.invoiceParser = invoiceParser;
    this.invoiceIngestionService = invoiceIngestionService;
    this.objectMapper = objectMapper;
    this.sourceEvidenceService = sourceEvidenceService;
    this.canonicalRepository = canonicalRepository;
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token) {
    this(client, environment.name(), nip, token, null, null, null, null, null);
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token,
      KsefInvoiceXmlParser parser,
      AccountingInvoiceIngestionService ingestion,
      ObjectMapper objectMapper) {
    this(client, environment.name(), nip, token, parser, ingestion, objectMapper, null, null);
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token,
      KsefInvoiceXmlParser parser,
      AccountingInvoiceIngestionService ingestion,
      ObjectMapper objectMapper,
      AccountingSourceEvidenceService sources) {
    this(client, environment.name(), nip, token, parser, ingestion, objectMapper, sources, null);
  }

  public KsefConnectionController(
      com.smartbox.investory.integrations.ksef.KsefClient client,
      KsefEnvironment environment,
      String nip,
      String token,
      KsefInvoiceXmlParser parser,
      AccountingInvoiceIngestionService ingestion,
      ObjectMapper objectMapper,
      AccountingSourceEvidenceService sources,
      AccountingPocRepository canonicalRepository) {
    this(
        client,
        environment.name(),
        nip,
        token,
        parser,
        ingestion,
        objectMapper,
        sources,
        canonicalRepository);
  }

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

  public String readInvoices(
      @RequestParam long profileId,
      @RequestParam(required = false) String month,
      RedirectAttributes redirectAttributes) {
    try {
      validateToken();
      LocalDate selectedMonth = parseMonth(month);
      KsefAccess access = client.authenticateWithToken(environment, nip, token);
      List<String> pages =
          queryIncomingPages(access.accessToken(), java.time.YearMonth.from(selectedMonth));
      List<String> ksefNumbers = new ArrayList<>();
      pages.forEach(page -> ksefNumbers.addAll(extractKsefNumbers(page)));
      ImportResult result =
          importIncomingInvoices(profileId, access.accessToken(), selectedMonth, ksefNumbers);
      redirectAttributes.addFlashAttribute("ksefInvoicesJson", mergePages(pages));
      redirectAttributes.addFlashAttribute(
          "ksefConnectionMessage", importSummary("KSeF metadata read", result));
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "ksefInvoiceReadError", "KSeF invoice read failed: " + safeMessage(exception));
    }
    return redirectToAccounting(month);
  }

  @Override
  @Transactional
  public AccountingUserApi.KsefSyncResult sync(long profileId, java.time.YearMonth month) {
    validateToken();
    KsefAccess access = client.authenticateWithToken(environment, nip, token);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0);
    java.time.YearMonth cursor = month;
    while (!cursor.isBefore(java.time.YearMonth.of(month.getYear(), 1))) {
      List<String> numbers = new ArrayList<>();
      queryIncomingPages(access.accessToken(), cursor)
          .forEach(page -> numbers.addAll(extractKsefNumbers(page)));
      queryPages(access.accessToken(), cursor, "Subject1")
          .forEach(page -> numbers.addAll(extractKsefNumbers(page)));
      Set<String> uniqueNumbers = new LinkedHashSet<>(numbers);
      boolean alreadyLoaded =
          !uniqueNumbers.isEmpty()
              && uniqueNumbers.stream().allMatch(n -> canonicalExists(profileId, n));
      result =
          result.plus(
              importIncomingInvoices(
                  profileId,
                  access.accessToken(),
                  cursor.atDay(1),
                  new ArrayList<>(uniqueNumbers)));
      if (alreadyLoaded) break;
      cursor = cursor.minusMonths(1);
    }
    return new AccountingUserApi.KsefSyncResult(
        "COMPLETED",
        result.received(),
        result.imported(),
        result.duplicates(),
        result.reviewRequired(),
        result.failed(),
        importSummary("KSeF sync finished", result));
  }

  @Override
  @Transactional
  public AccountingUserApi.KsefSyncResult syncAll(long profileId, java.time.YearMonth startMonth) {
    validateToken();
    KsefAccess access = client.authenticateWithToken(environment, nip, token);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0);
    java.time.YearMonth cursor = startMonth;
    while (cursor != null && !cursor.isBefore(java.time.YearMonth.of(2000, 1))) {
      Set<String> incoming = new LinkedHashSet<>();
      queryIncomingPages(access.accessToken(), cursor)
          .forEach(page -> incoming.addAll(extractKsefNumbers(page)));
      Set<String> seller = new LinkedHashSet<>();
      queryPages(access.accessToken(), cursor, "Subject1")
          .forEach(page -> seller.addAll(extractKsefNumbers(page)));
      Set<String> thirdParty = new LinkedHashSet<>();
      queryPages(access.accessToken(), cursor, "Subject3")
          .forEach(page -> thirdParty.addAll(extractKsefNumbers(page)));

      Set<String> all = new LinkedHashSet<>(incoming);
      all.addAll(seller);
      all.addAll(thirdParty);
      // A quiet current month is normal. Keep walking backwards; the stop condition is
      // an already-loaded discovered set (or the bounded historical floor below).
      if (all.isEmpty()) {
        cursor = cursor.minusMonths(1);
        continue;
      }
      boolean alreadyLoaded = all.stream().allMatch(n -> canonicalExists(profileId, n));
      result =
          result.plus(
              importIncomingInvoices(
                  profileId, access.accessToken(), cursor.atDay(1), new ArrayList<>(incoming)));
      result = result.plus(importSellerInvoices(profileId, access.accessToken(), seller));
      result = result.plus(importThirdPartyEvidence(profileId, access.accessToken(), thirdParty));
      if (alreadyLoaded) break;
      cursor = cursor.minusMonths(1);
    }
    return new AccountingUserApi.KsefSyncResult(
        "COMPLETED",
        result.received(),
        result.imported(),
        result.duplicates(),
        result.reviewRequired(),
        result.failed(),
        importSummary(
            "KSeF history sync finished (sales, purchases, corrections, and third-party evidence)",
            result));
  }

  /**
   * Re-imports KSeF documents through source evidence -> staging -> reconciliation -> promotion.
   */
  @Override
  @Transactional
  public AccountingUserApi.KsefSyncResult reimport(long profileId, java.time.YearMonth month) {
    validateToken();
    if (invoiceParser == null
        || sourceEvidenceService == null
        || stagingAcquisition == null
        || stagingReconciliation == null
        || stagingPromotion == null) {
      return new AccountingUserApi.KsefSyncResult(
          "NOT_SUPPORTED", 0, 0, 0, 0, 0, "Staging re-import is not configured.");
    }
    KsefAccess access = client.authenticateWithToken(environment, nip, token);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0);
    Set<java.time.LocalDate> periods = new java.util.LinkedHashSet<>();
    for (java.time.YearMonth cursor = month;
        !cursor.isBefore(java.time.YearMonth.of(month.getYear(), 1));
        cursor = cursor.minusMonths(1)) {
      List<String> numbers = new ArrayList<>();
      queryIncomingPages(access.accessToken(), cursor)
          .forEach(page -> numbers.addAll(extractKsefNumbers(page)));
      result =
          result.plus(
              reimportIncoming(
                  profileId, access.accessToken(), cursor, new LinkedHashSet<>(numbers), periods));
    }
    periods.stream()
        .sorted()
        .forEach(
            period -> {
              stagingReconciliation.reconcile(profileId, period);
              stagingPromotion.promoteNew(profileId, period);
            });
    return new AccountingUserApi.KsefSyncResult(
        "COMPLETED",
        result.received(),
        result.imported(),
        result.duplicates(),
        result.reviewRequired(),
        result.failed(),
        importSummary("KSeF evidence-backed re-import finished", result));
  }

  private ImportResult reimportIncoming(
      long profileId,
      String accessToken,
      java.time.YearMonth discoveryMonth,
      Set<String> ksefNumbers,
      Set<java.time.LocalDate> periods) {
    int imported = 0;
    int reviewRequired = 0;
    int failed = 0;
    for (String ksefNumber : ksefNumbers) {
      try {
        String xml = client.downloadInvoice(environment, accessToken, ksefNumber);
        byte[] payload = xml.getBytes(StandardCharsets.UTF_8);
        var invoice = invoiceParser.parse(payload);
        long sourceId =
            sourceEvidenceService.receiveKsef(profileId, ksefNumber, invoice.issueDate(), payload);
        sourceEvidenceService.status(sourceId, AccountingSourceStatus.PARSED, null);
        boolean sale = nip != null && nip.equals(invoice.sellerNip());
        if (!isSupportedAutomaticType(invoice.invoiceType(), sale)
            || (!sale && (invoice.category() == null || invoice.vatDeductionRatio() == null))) {
          sourceEvidenceService.status(
              sourceId,
              AccountingSourceStatus.REVIEW_REQUIRED,
              "KSeF document requires tax classification before staging");
          reviewRequired++;
          continue;
        }
        String counterparty =
            sale
                ? firstNonBlank(invoice.buyerName(), invoice.buyerNip())
                : firstNonBlank(invoice.sellerName(), invoice.sellerNip());
        String currency = invoice.currency() == null ? "PLN" : invoice.currency();
        String documentType =
            "KOR".equalsIgnoreCase(invoice.invoiceType())
                ? "CREDIT_NOTE"
                : (sale ? "SALES_INVOICE" : "PURCHASE_INVOICE");
        var taxPeriod =
            AccountingDateRules.accountingPeriod(
                invoice.saleDate(), invoice.issueDate(), null, false);
        var reviewed =
            new ReviewedInvoice(
                taxPeriod,
                documentType,
                invoice.issueDate(),
                invoice.saleDate(),
                invoice.reference(),
                counterparty,
                sale ? null : invoice.category(),
                currency,
                invoice.netAmount(),
                invoice.vatAmount(),
                invoice.grossAmount(),
                sale ? null : invoice.vatDeductionRatio(),
                "KSEF_SOURCE_DOCUMENT",
                "KSeF " + ksefNumber + "; counterparty " + counterparty,
                Long.toString(sourceId),
                sale ? invoice.buyerNip() : invoice.sellerNip(),
                "PL",
                ksefNumber,
                new AccountingFilingEvidence(AccountingFilingEvidence.Type.KSEF, ksefNumber),
                null,
                invoice.vatRate());
        String treatment =
            sale
                ? ("PLN".equalsIgnoreCase(currency) ? "DOMESTIC_VAT" : "EU_B2B_REVERSE_CHARGE")
                : ("PLN".equalsIgnoreCase(currency)
                    ? "DOMESTIC_PURCHASE"
                    : "IMPORT_OF_SERVICES_EU");
        canonicalRepository.enrichLegacyDocumentFromKsef(
            profileId,
            sale ? "SALE" : "PURCHASE",
            invoice.reference(),
            sale ? invoice.issueDate() : firstNonBlankDate(invoice.issueDate(), invoice.saleDate()),
            currency.trim().toUpperCase(),
            sale && "CREDIT_NOTE".equals(documentType)
                ? invoice.netAmount().negate()
                : invoice.netAmount(),
            sale && "CREDIT_NOTE".equals(documentType)
                ? invoice.vatAmount().negate()
                : invoice.vatAmount(),
            sale && "CREDIT_NOTE".equals(documentType)
                ? invoice.grossAmount().negate()
                : invoice.grossAmount(),
            sourceId,
            ksefNumber,
            sale ? invoice.buyerNip() : invoice.sellerNip(),
            "PL",
            reviewed.note());
        stagingAcquisition.stageInvoice(profileId, reviewed, treatment);
        periods.add(taxPeriod);
        imported++;
      } catch (RuntimeException exception) {
        failed++;
      }
    }
    return new ImportResult(ksefNumbers.size(), imported, 0, reviewRequired, failed);
  }

  private LocalDate firstNonBlankDate(LocalDate first, LocalDate second) {
    return first != null ? first : second;
  }

  @Override
  @Transactional
  public AccountingUserApi.KsefSyncResult syncSeller(long profileId, java.time.YearMonth month) {
    validateToken();
    KsefAccess access = client.authenticateWithToken(environment, nip, token);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0);
    java.time.YearMonth cursor = month;
    while (!cursor.isBefore(java.time.YearMonth.of(month.getYear(), 1))) {
      List<String> numbers = new ArrayList<>();
      queryPages(access.accessToken(), cursor, "Subject1")
          .forEach(page -> numbers.addAll(extractKsefNumbers(page)));
      Set<String> uniqueNumbers = new LinkedHashSet<>(numbers);
      boolean alreadyLoaded =
          !uniqueNumbers.isEmpty()
              && uniqueNumbers.stream().allMatch(n -> canonicalExists(profileId, n));
      result = result.plus(importSellerInvoices(profileId, access.accessToken(), uniqueNumbers));
      if (alreadyLoaded) break;
      cursor = cursor.minusMonths(1);
    }
    return new AccountingUserApi.KsefSyncResult(
        "COMPLETED",
        result.received(),
        result.imported(),
        result.duplicates(),
        result.reviewRequired(),
        result.failed(),
        importSummary(
            "KSeF seller sync finished (discovery month uses issue date; accounting month uses sale date when present)",
            result));
  }

  @Override
  @Transactional
  public AccountingUserApi.KsefSyncResult syncThirdParty(
      long profileId, java.time.YearMonth month) {
    validateToken();
    KsefAccess access = client.authenticateWithToken(environment, nip, token);
    ImportResult result = new ImportResult(0, 0, 0, 0, 0);
    java.time.YearMonth cursor = month;
    while (!cursor.isBefore(java.time.YearMonth.of(month.getYear(), 1))) {
      List<String> numbers = new ArrayList<>();
      queryPages(access.accessToken(), cursor, "Subject3")
          .forEach(page -> numbers.addAll(extractKsefNumbers(page)));
      Set<String> uniqueNumbers = new LinkedHashSet<>(numbers);
      boolean alreadyLoaded =
          !uniqueNumbers.isEmpty()
              && uniqueNumbers.stream().allMatch(n -> canonicalExists(profileId, n));
      result =
          result.plus(importThirdPartyEvidence(profileId, access.accessToken(), uniqueNumbers));
      if (alreadyLoaded) break;
      cursor = cursor.minusMonths(1);
    }
    return new AccountingUserApi.KsefSyncResult(
        "COMPLETED",
        result.received(),
        result.imported(),
        result.duplicates(),
        result.reviewRequired(),
        result.failed(),
        importSummary("KSeF third-party sync finished", result));
  }

  private String importSummary(String prefix, ImportResult result) {
    return prefix
        + ": "
        + result.received()
        + " received, "
        + result.imported()
        + " imported, "
        + result.duplicates()
        + " duplicates, "
        + result.reviewRequired()
        + " need review, "
        + result.failed()
        + " failed.";
  }

  private List<String> queryIncomingPages(String accessToken, java.time.YearMonth month) {
    List<String> pages = new ArrayList<>();
    for (int page = 0; page < KSEF_MAX_PAGES; page++) {
      String invoices =
          client.queryIncomingInvoices(
              environment,
              accessToken,
              month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC),
              month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC),
              page,
              KSEF_PAGE_SIZE);
      pages.add(invoices);
      if (extractKsefNumbers(invoices).size() < KSEF_PAGE_SIZE) break;
    }
    return pages;
  }

  private List<String> queryPages(
      String accessToken, java.time.YearMonth month, String subjectType) {
    List<String> pages = new ArrayList<>();
    for (int page = 0; page < KSEF_MAX_PAGES; page++) {
      String invoices =
          client.queryInvoices(
              environment,
              accessToken,
              subjectType,
              month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC),
              month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC),
              page,
              KSEF_PAGE_SIZE);
      pages.add(invoices);
      if (extractKsefNumbers(invoices).size() < KSEF_PAGE_SIZE) break;
    }
    return pages;
  }

  private ImportResult importSellerInvoices(
      long profileId, String accessToken, Set<String> ksefNumbers) {
    if (invoiceParser == null || invoiceIngestionService == null || sourceEvidenceService == null) {
      return new ImportResult(ksefNumbers.size(), 0, 0, 0, ksefNumbers.size());
    }
    int imported = 0;
    int duplicates = 0;
    int reviewRequired = 0;
    int failed = 0;
    for (String ksefNumber : ksefNumbers) {
      long sourceId = 0;
      try {
        var existing =
            sourceEvidenceService.findId(profileId, AccountingSourceType.KSEF, ksefNumber);
        if (existing.isPresent()
            && canonicalRepository != null
            && canonicalRepository.canonicalDocumentExists(
                profileId, existing.get(), ksefNumber, null)) {
          duplicates++;
          continue;
        }
        String xml = client.downloadInvoice(environment, accessToken, ksefNumber);
        byte[] payload = xml.getBytes(StandardCharsets.UTF_8);
        KsefInvoiceXmlParser.ParsedKsefInvoice invoice = invoiceParser.parse(payload);
        sourceId =
            sourceEvidenceService.receiveKsef(profileId, ksefNumber, invoice.issueDate(), payload);
        validateSellerInvoice(invoice);
        sourceEvidenceService.status(sourceId, AccountingSourceStatus.PARSED, null);
        if (!isSupportedAutomaticType(invoice.invoiceType(), true)) {
          sourceEvidenceService.status(
              sourceId,
              AccountingSourceStatus.REVIEW_REQUIRED,
              "Invoice type "
                  + displayType(invoice.invoiceType())
                  + " requires review before sales import");
          reviewRequired++;
          continue;
        }
        boolean correction =
            "KOR".equalsIgnoreCase(invoice.invoiceType())
                || (invoice.reference() != null && invoice.reference().startsWith("FK"));
        String documentType = correction ? "CREDIT_NOTE" : "SALES_INVOICE";
        String buyer = firstNonBlank(invoice.buyerName(), invoice.buyerNip());
        LocalDate accountingTaxPeriod =
            AccountingDateRules.accountingPeriod(
                invoice.saleDate(), invoice.issueDate(), null, correction);
        boolean saved =
            invoiceIngestionService.ingest(
                profileId,
                new ReviewedInvoice(
                    accountingTaxPeriod,
                    documentType,
                    invoice.issueDate(),
                    invoice.saleDate(),
                    invoice.reference(),
                    buyer,
                    null,
                    invoice.currency(),
                    absolute(invoice.netAmount()),
                    absolute(invoice.vatAmount()),
                    absolute(invoice.grossAmount()),
                    null,
                    "KSEF_SOURCE_DOCUMENT",
                    "KSeF " + ksefNumber + "; buyer " + buyer,
                    Long.toString(sourceId),
                    invoice.buyerNip(),
                    "PL",
                    ksefNumber,
                    new AccountingFilingEvidence(AccountingFilingEvidence.Type.KSEF, ksefNumber),
                    null,
                    invoice.vatRate()));
        if (saved) {
          imported++;
          sourceEvidenceService.status(sourceId, AccountingSourceStatus.IMPORTED, null);
        } else {
          duplicates++;
          sourceEvidenceService.status(
              sourceId, AccountingSourceStatus.IMPORTED, "Canonical row already exists");
        }
      } catch (RuntimeException exception) {
        if (sourceId != 0) {
          sourceEvidenceService.status(
              sourceId, AccountingSourceStatus.FAILED, safeMessage(exception));
        }
        failed++;
      }
    }
    return new ImportResult(ksefNumbers.size(), imported, duplicates, reviewRequired, failed);
  }

  private ImportResult importThirdPartyEvidence(
      long profileId, String accessToken, Set<String> ksefNumbers) {
    if (sourceEvidenceService == null) {
      return new ImportResult(ksefNumbers.size(), 0, 0, 0, ksefNumbers.size());
    }
    int duplicates = 0;
    int reviewRequired = 0;
    int failed = 0;
    for (String ksefNumber : ksefNumbers) {
      long sourceId = 0;
      try {
        var existing =
            sourceEvidenceService.findId(profileId, AccountingSourceType.KSEF, ksefNumber);
        if (existing.isPresent()
            && canonicalRepository != null
            && canonicalRepository.canonicalDocumentExists(
                profileId, existing.get(), ksefNumber, null)) {
          duplicates++;
          continue;
        }
        byte[] payload =
            client
                .downloadInvoice(environment, accessToken, ksefNumber)
                .getBytes(StandardCharsets.UTF_8);
        KsefInvoiceXmlParser.ParsedKsefInvoice invoice = invoiceParser.parse(payload);
        sourceId =
            sourceEvidenceService.receiveKsef(profileId, ksefNumber, invoice.issueDate(), payload);
        validateSellerInvoice(invoice);
        sourceEvidenceService.status(
            sourceId,
            AccountingSourceStatus.REVIEW_REQUIRED,
            "Subject3 document requires manual role and tax review");
        reviewRequired++;
      } catch (RuntimeException exception) {
        if (sourceId != 0)
          sourceEvidenceService.status(
              sourceId, AccountingSourceStatus.FAILED, safeMessage(exception));
        failed++;
      }
    }
    return new ImportResult(ksefNumbers.size(), 0, duplicates, reviewRequired, failed);
  }

  private boolean isSupportedAutomaticType(String invoiceType, boolean seller) {
    String type = invoiceType == null || invoiceType.isBlank() ? "VAT" : invoiceType.trim();
    return "KOR".equalsIgnoreCase(type) || "VAT".equalsIgnoreCase(type);
  }

  private String displayType(String invoiceType) {
    return invoiceType == null || invoiceType.isBlank() ? "UNKNOWN" : invoiceType;
  }

  private void validateSellerInvoice(KsefInvoiceXmlParser.ParsedKsefInvoice invoice) {
    if (invoice.reference() == null || invoice.reference().isBlank())
      throw new IllegalArgumentException("Seller KSeF invoice is missing invoice number");
    if (invoice.issueDate() == null)
      throw new IllegalArgumentException("Seller KSeF invoice is missing issue date");
    if (firstNonBlank(invoice.buyerName(), invoice.buyerNip()) == null)
      throw new IllegalArgumentException("Seller KSeF invoice is missing buyer");
    if (invoice.currency() == null || invoice.currency().isBlank())
      throw new IllegalArgumentException("Seller KSeF invoice is missing currency");
    if (invoice.netAmount() == null || invoice.vatAmount() == null || invoice.grossAmount() == null)
      throw new IllegalArgumentException("Seller KSeF invoice is missing net, VAT or gross amount");
  }

  private java.math.BigDecimal absolute(java.math.BigDecimal value) {
    return value.abs();
  }

  private boolean canonicalExists(long profileId, String ksefNumber) {
    if (sourceEvidenceService == null || canonicalRepository == null) return false;
    var source = sourceEvidenceService.findId(profileId, AccountingSourceType.KSEF, ksefNumber);
    return source.isPresent()
        && canonicalRepository.canonicalDocumentExists(profileId, source.get(), ksefNumber, null);
  }

  @Override
  public String providerStatus() {
    return token == null || token.isBlank() || DEFAULT_TOKEN.equals(token)
        ? "NOT_CONFIGURED"
        : "CONNECTED";
  }

  private ImportResult importIncomingInvoices(
      long profileId, String accessToken, LocalDate taxPeriod, List<String> ksefNumbers) {
    if (invoiceParser == null || invoiceIngestionService == null) {
      return new ImportResult(0, 0, 0, 0, 0);
    }
    int imported = 0;
    int duplicates = 0;
    int reviewRequired = 0;
    int failed = 0;
    for (String ksefNumber : ksefNumbers) {
      long sourceId = 0;
      try {
        var existingBeforeDownload =
            sourceEvidenceService == null
                ? java.util.Optional.<Long>empty()
                : sourceEvidenceService.findId(profileId, AccountingSourceType.KSEF, ksefNumber);
        if (existingBeforeDownload.isPresent()
            && canonicalRepository != null
            && canonicalRepository.canonicalDocumentExists(
                profileId, existingBeforeDownload.get(), ksefNumber, null)) {
          duplicates++;
          continue;
        }
        String xml = client.downloadInvoice(environment, accessToken, ksefNumber);
        var invoice = invoiceParser.parse(xml.getBytes(StandardCharsets.UTF_8));
        var existing =
            sourceEvidenceService == null
                ? java.util.Optional.<Long>empty()
                : sourceEvidenceService.findId(profileId, AccountingSourceType.KSEF, ksefNumber);
        if (existing.isPresent()
            && canonicalRepository != null
            && canonicalRepository.canonicalDocumentExists(
                profileId, existing.get(), ksefNumber, invoice.reference())) {
          duplicates++;
          continue;
        }
        sourceId =
            sourceEvidenceService == null
                ? 0
                : sourceEvidenceService.receiveKsef(
                    profileId,
                    ksefNumber,
                    invoice.issueDate(),
                    xml.getBytes(StandardCharsets.UTF_8));
        if (sourceId != 0)
          sourceEvidenceService.status(sourceId, AccountingSourceStatus.PARSED, null);
        if (!isSupportedAutomaticType(invoice.invoiceType(), false)) {
          if (sourceId != 0)
            sourceEvidenceService.status(
                sourceId,
                AccountingSourceStatus.REVIEW_REQUIRED,
                "Invoice type "
                    + displayType(invoice.invoiceType())
                    + " requires review before purchase import");
          reviewRequired++;
          continue;
        }
        if (invoice.category() == null || invoice.vatDeductionRatio() == null) {
          if (sourceId != 0)
            sourceEvidenceService.status(
                sourceId,
                AccountingSourceStatus.REVIEW_REQUIRED,
                "Tax category or VAT deduction is not proven");
          reviewRequired++;
          continue;
        }
        String supplier = firstNonBlank(invoice.sellerName(), invoice.sellerNip());
        String currency = invoice.currency() == null ? "PLN" : invoice.currency();
        boolean saved =
            invoiceIngestionService.ingest(
                profileId,
                new ReviewedInvoice(
                    AccountingDateRules.accountingPeriod(
                        invoice.saleDate(), invoice.issueDate(), null, false),
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
                    sourceId == 0 ? null : Long.toString(sourceId),
                    invoice.sellerNip(),
                    "PL",
                    ksefNumber,
                    new AccountingFilingEvidence(AccountingFilingEvidence.Type.KSEF, ksefNumber),
                    null,
                    invoice.vatRate()));
        if (saved) imported++;
        if (sourceId != 0)
          sourceEvidenceService.status(sourceId, AccountingSourceStatus.IMPORTED, null);
      } catch (RuntimeException exception) {
        if (sourceId == 0 && sourceEvidenceService != null) {
          try {
            // Keep failed retrieval evidence separate from the immutable successful KSeF
            // identity. A zero-byte row under ksefNumber permanently blocked retry via the
            // source uniqueness constraint.
            sourceId =
                sourceEvidenceService.receiveKsef(
                    profileId,
                    "FAILED:" + ksefNumber + ":" + java.util.UUID.randomUUID(),
                    null,
                    new byte[0]);
          } catch (RuntimeException ignored) {
            // Preserve the original KSeF failure if the failure marker itself cannot be stored.
          }
        }
        if (sourceId != 0 && sourceEvidenceService != null) {
          sourceEvidenceService.status(
              sourceId, AccountingSourceStatus.FAILED, exception.getMessage());
        }
        failed++;
      }
    }
    return new ImportResult(ksefNumbers.size(), imported, duplicates, reviewRequired, failed);
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

  private record ImportResult(
      int received, int imported, int duplicates, int reviewRequired, int failed) {
    private ImportResult plus(ImportResult other) {
      return new ImportResult(
          received + other.received,
          imported + other.imported,
          duplicates + other.duplicates,
          reviewRequired + other.reviewRequired,
          failed + other.failed);
    }
  }

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
