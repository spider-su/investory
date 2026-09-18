package com.smartbox.investory.poc.accounting.ksef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.integrations.ksef.KsefClient;
import com.smartbox.investory.integrations.ksef.KsefClient.KsefAccess;
import com.smartbox.investory.integrations.ksef.KsefEnvironment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import tools.jackson.databind.ObjectMapper;

class KsefConnectionControllerTest {

  @Test
  void authenticatesConfiguredKsefTokenAndRedirectsWithSuccess() {
    com.smartbox.investory.integrations.ksef.KsefClient client =
        org.mockito.Mockito.mock(com.smartbox.investory.integrations.ksef.KsefClient.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    KsefConnectionController controller =
        new KsefConnectionController(client, KsefEnvironment.TEST, "1234567890", "secret-token");
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String redirect = controller.testConnection("2026-07", attributes);

    assertThat(redirect).isEqualTo("redirect:/poc/accounting?month=2026-07");
    assertThat(attributes.getFlashAttributes().get("ksefConnectionMessage"))
        .isEqualTo("KSeF authentication succeeded.");
    verify(client).authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token");
  }

  @Test
  void sellerSyncQueriesSubject1UsesBuyerAndSaleMonthForAccounting() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    when(client.authenticateWithToken(KsefEnvironment.PRODUCTION, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryInvoices(
            eq(KsefEnvironment.PRODUCTION),
            eq("access-token"),
            eq("Subject1"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-SALE-1\"}]}");
    when(client.downloadInvoice(KsefEnvironment.PRODUCTION, "access-token", "KSEF-SALE-1"))
        .thenReturn("<Invoice/>");
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-SALE-1")).thenReturn(Optional.empty());
    when(sources.receiveKsef(
            1L,
            eq("KSEF-SALE-1"),
            eq(LocalDate.of(2026, 7, 10)),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(42L);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-SALE-1",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 6, 29),
                "8133703437",
                "Seller",
                "9452196213",
                "Buyer",
                "PLN",
                new BigDecimal("100"),
                new BigDecimal("23"),
                new BigDecimal("123"),
                null,
                null,
                "VAT"));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    KsefConnectionController controller =
        new KsefConnectionController(
            client,
            KsefEnvironment.PRODUCTION,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources,
            org.mockito.Mockito.mock(AccountingPocRepository.class));

    var result = controller.syncSeller(1L, java.time.YearMonth.of(2026, 7));

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.message()).contains("discovery month uses issue date");
    ArgumentCaptor<ReviewedInvoice> invoice = ArgumentCaptor.forClass(ReviewedInvoice.class);
    verify(ingestion).ingest(1L, invoice.capture());
    assertThat(invoice.getValue().documentType()).isEqualTo("SALES_INVOICE");
    assertThat(invoice.getValue().counterpartyAlias()).isEqualTo("Buyer");
    assertThat(invoice.getValue().taxPeriod()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(invoice.getValue().issueDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(invoice.getValue().saleDate()).isEqualTo(LocalDate.of(2026, 6, 29));
    verify(client)
        .queryInvoices(
            eq(KsefEnvironment.PRODUCTION),
            eq("access-token"),
            eq("Subject1"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250));
  }

  @Test
  void sellerCreditNoteUsesIssueMonthForAccounting() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    when(client.authenticateWithToken(KsefEnvironment.PRODUCTION, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryInvoices(
            eq(KsefEnvironment.PRODUCTION),
            eq("access-token"),
            eq("Subject1"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-CREDIT-1\"}]}");
    when(client.downloadInvoice(KsefEnvironment.PRODUCTION, "access-token", "KSEF-CREDIT-1"))
        .thenReturn("<Invoice/>");
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-CREDIT-1"))
        .thenReturn(Optional.empty());
    when(sources.receiveKsef(
            1L,
            eq("KSEF-CREDIT-1"),
            eq(LocalDate.of(2026, 7, 11)),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(43L);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FK1",
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2026, 6, 30),
                "8133703437",
                "Seller",
                "9452196213",
                "Buyer",
                "PLN",
                new BigDecimal("20"),
                new BigDecimal("4.60"),
                new BigDecimal("24.60"),
                null,
                null,
                "KOR"));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    KsefConnectionController controller =
        new KsefConnectionController(
            client,
            KsefEnvironment.PRODUCTION,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources,
            org.mockito.Mockito.mock(AccountingPocRepository.class));

    var result = controller.syncSeller(1L, java.time.YearMonth.of(2026, 7));

    assertThat(result.imported()).isEqualTo(1);
    ArgumentCaptor<ReviewedInvoice> invoice = ArgumentCaptor.forClass(ReviewedInvoice.class);
    verify(ingestion).ingest(1L, invoice.capture());
    assertThat(invoice.getValue().documentType()).isEqualTo("CREDIT_NOTE");
    assertThat(invoice.getValue().taxPeriod()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(invoice.getValue().issueDate()).isEqualTo(LocalDate.of(2026, 7, 11));
    assertThat(invoice.getValue().saleDate()).isEqualTo(LocalDate.of(2026, 6, 30));
  }

  @Test
  void sellerInvoiceFallsBackToIssueMonthWhenSaleDateIsMissing() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    when(client.authenticateWithToken(KsefEnvironment.PRODUCTION, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryInvoices(
            eq(KsefEnvironment.PRODUCTION),
            eq("access-token"),
            eq("Subject1"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-NO-SALE-DATE\"}]}");
    when(client.downloadInvoice(KsefEnvironment.PRODUCTION, "access-token", "KSEF-NO-SALE-DATE"))
        .thenReturn("<Invoice/>");
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-NO-SALE-DATE"))
        .thenReturn(Optional.empty());
    when(sources.receiveKsef(
            1L,
            eq("KSEF-NO-SALE-DATE"),
            eq(LocalDate.of(2026, 7, 10)),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(44L);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-NO-SALE-DATE",
                LocalDate.of(2026, 7, 12),
                null,
                "8133703437",
                "Seller",
                "9452196213",
                "Buyer",
                "PLN",
                new BigDecimal("30"),
                new BigDecimal("6.90"),
                new BigDecimal("36.90"),
                null,
                null,
                "VAT"));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    KsefConnectionController controller =
        new KsefConnectionController(
            client,
            KsefEnvironment.PRODUCTION,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources,
            org.mockito.Mockito.mock(AccountingPocRepository.class));

    controller.syncSeller(1L, java.time.YearMonth.of(2026, 7));

    ArgumentCaptor<ReviewedInvoice> invoice = ArgumentCaptor.forClass(ReviewedInvoice.class);
    verify(ingestion).ingest(1L, invoice.capture());
    assertThat(invoice.getValue().taxPeriod()).isEqualTo(LocalDate.of(2026, 7, 1));
  }

  @Test
  void rejectsPlaceholderTokenWithoutCallingKsef() {
    com.smartbox.investory.integrations.ksef.KsefClient client =
        org.mockito.Mockito.mock(com.smartbox.investory.integrations.ksef.KsefClient.class);
    KsefConnectionController controller =
        new KsefConnectionController(
            client, KsefEnvironment.TEST, "1234567890", "change-me-ksef-token");
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    controller.testConnection(null, attributes);

    assertThat(attributes.getFlashAttributes().get("ksefConnectionError"))
        .isEqualTo("KSeF connection failed: app.ksef.token is not configured");
    org.mockito.Mockito.verifyNoInteractions(client);
  }

  @Test
  void readsIncomingInvoicesForSelectedMonthWithoutPersistence() {
    com.smartbox.investory.integrations.ksef.KsefClient client =
        org.mockito.Mockito.mock(com.smartbox.investory.integrations.ksef.KsefClient.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
            eq(OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[]}");
    KsefConnectionController controller =
        new KsefConnectionController(client, KsefEnvironment.TEST, "1234567890", "secret-token");
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String redirect = controller.readInvoices(1L, "2026-07", attributes);

    assertThat(redirect).isEqualTo("redirect:/poc/accounting?month=2026-07");
    assertThat(attributes.getFlashAttributes().get("ksefInvoicesJson"))
        .isEqualTo("{\"invoices\":[]}");
    verify(client)
        .queryIncomingInvoices(
            KsefEnvironment.TEST,
            "access-token",
            OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            0,
            250);
  }

  @Test
  void downloadsAndPersistsStructuredIncomingInvoiceWithProvenance() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-1\"}]}");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-1"))
        .thenReturn("<Invoice/>");
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-1",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                "1111111111",
                "Supplier",
                "1234567890",
                "Buyer",
                "PLN",
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00")));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    KsefConnectionController controller =
        new KsefConnectionController(
            client,
            KsefEnvironment.TEST,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper());
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    controller.readInvoices(1L, "2026-07", attributes);

    ArgumentCaptor<ReviewedInvoice> captor = ArgumentCaptor.forClass(ReviewedInvoice.class);
    verify(ingestion).ingest(1L, captor.capture());
    assertThat(captor.getValue().reference()).isEqualTo("FV-1");
    assertThat(captor.getValue().sourceQuality()).isEqualTo("KSEF_SOURCE_DOCUMENT");
    assertThat(captor.getValue().note()).contains("KSeF KSEF-1");
    assertThat(attributes.getFlashAttributes().get("ksefConnectionMessage"))
        .isEqualTo(
            "KSeF metadata read: 1 received, 1 imported, 0 duplicates, 0 need review, 0 failed.");
  }

  @Test
  void leavesUnknownKsefTaxClassificationForReview() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-UNKNOWN\"}]}");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-UNKNOWN"))
        .thenReturn("<Invoice/>");
    when(sources.receiveKsef(
            1L,
            eq("KSEF-UNKNOWN"),
            eq(LocalDate.of(2026, 7, 10)),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(10L);
    when(sources.status(10L)).thenReturn(AccountingSourceStatus.RECEIVED);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-UNKNOWN",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                "1111111111",
                "Supplier",
                "1234567890",
                "Buyer",
                "PLN",
                new BigDecimal("100"),
                new BigDecimal("23"),
                new BigDecimal("123"),
                null,
                null));

    KsefConnectionController controller =
        new KsefConnectionController(
            client,
            KsefEnvironment.TEST,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources);

    controller.readInvoices(1L, "2026-07", new RedirectAttributesModelMap());

    org.mockito.Mockito.verifyNoInteractions(ingestion);
    org.mockito.Mockito.verify(sources)
        .status(
            10L,
            AccountingSourceStatus.REVIEW_REQUIRED,
            "Tax category or VAT deduction is not proven");
  }

  @Test
  void preservesKsefParseFailureAsFailedSource() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-BAD\"}]}");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-BAD"))
        .thenReturn("<broken/>");
    when(sources.receiveKsef(
            1L,
            org.mockito.ArgumentMatchers.startsWith("FAILED:KSEF-BAD:"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(11L);
    when(sources.status(11L)).thenReturn(AccountingSourceStatus.RECEIVED);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalArgumentException("invalid KSeF XML"));

    new KsefConnectionController(
            client,
            KsefEnvironment.TEST,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources)
        .readInvoices(1L, "2026-07", new RedirectAttributesModelMap());

    verify(sources).status(11L, AccountingSourceStatus.FAILED, "invalid KSeF XML");
    org.mockito.Mockito.verifyNoInteractions(ingestion);
  }

  @Test
  void skipsImportedKsefSourceBeforeDownloadingAgain() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    AccountingPocRepository canonical = org.mockito.Mockito.mock(AccountingPocRepository.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-IMPORTED\"}]}");
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-IMPORTED"))
        .thenReturn(Optional.of(12L));
    when(canonical.canonicalDocumentExists(1L, 12L, "KSEF-IMPORTED", null)).thenReturn(true);

    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
    new KsefConnectionController(
            client,
            KsefEnvironment.TEST,
            "1234567890",
            "secret-token",
            parser,
            ingestion,
            new ObjectMapper(),
            sources,
            canonical)
        .readInvoices(1L, "2026-07", attributes);

    org.mockito.Mockito.verifyNoInteractions(parser, ingestion);
    org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
        .downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-IMPORTED");
    assertThat(attributes.getFlashAttributes().get("ksefConnectionMessage"))
        .isEqualTo(
            "KSeF metadata read: 1 received, 0 imported, 1 duplicates, 0 need review, 0 failed.");
  }

  @Test
  void recoversSourceOnlyKsefEvidenceWhenCanonicalDocumentIsMissing() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    AccountingPocRepository canonical = org.mockito.Mockito.mock(AccountingPocRepository.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)),
            eq(0),
            eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-RECOVER\"}]}");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-RECOVER"))
        .thenReturn("<Invoice/>");
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-RECOVER"))
        .thenReturn(Optional.of(12L));
    when(canonical.canonicalDocumentExists(1L, 12L, "KSEF-RECOVER", "FV-RECOVER"))
        .thenReturn(false);
    when(sources.receiveKsef(
            1L,
            eq("KSEF-RECOVER"),
            eq(LocalDate.of(2026, 7, 10)),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(12L);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-RECOVER",
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10),
                "1111111111",
                "Supplier",
                "1234567890",
                "Buyer",
                "PLN",
                new BigDecimal("100"),
                new BigDecimal("23"),
                new BigDecimal("123")));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    var result =
        new KsefConnectionController(
                client,
                KsefEnvironment.TEST,
                "1234567890",
                "secret-token",
                parser,
                ingestion,
                new ObjectMapper(),
                sources,
                canonical)
            .readInvoices(1L, "2026-07", new RedirectAttributesModelMap());

    verify(client).downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-RECOVER");
    verify(ingestion).ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class));
  }

  @Test
  void globalSyncBackfillsEmptyGapAndStopsAtLoadedMonth() {
    KsefClient client = org.mockito.Mockito.mock(KsefClient.class);
    KsefInvoiceXmlParser parser = org.mockito.Mockito.mock(KsefInvoiceXmlParser.class);
    AccountingInvoiceIngestionService ingestion =
        org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
    AccountingSourceEvidenceService sources =
        org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
    AccountingPocRepository canonical = org.mockito.Mockito.mock(AccountingPocRepository.class);
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class),
            eq(0),
            eq(250)))
        .thenAnswer(
            invocation -> {
              OffsetDateTime start = invocation.getArgument(2);
              return switch (start.getMonthValue()) {
                case 5 -> "{\"invoices\":[{\"ksefNumber\":\"KSEF-1\"}]}";
                case 4 -> "{\"invoices\":[{\"ksefNumber\":\"KSEF-2\"}]}";
                case 3 -> "{\"invoices\":[{\"ksefNumber\":\"KSEF-3\"}]}";
                default -> "{\"invoices\":[]}";
              };
            });
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-3")).thenReturn(Optional.of(12L));
    when(canonical.canonicalDocumentExists(1L, 12L, "KSEF-3", null)).thenReturn(true);
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-1")).thenReturn(Optional.empty());
    when(sources.findId(1L, AccountingSourceType.KSEF, "KSEF-2")).thenReturn(Optional.empty());
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-1"))
        .thenReturn("<Invoice/>");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-2"))
        .thenReturn("<Invoice/>");
    when(sources.receiveKsef(
            1L,
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(10L);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new KsefInvoiceXmlParser.ParsedKsefInvoice(
                "FV-1",
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 10),
                "1111111111",
                "Supplier",
                "1234567890",
                "Buyer",
                "PLN",
                new BigDecimal("100"),
                new BigDecimal("23"),
                new BigDecimal("123")));
    when(ingestion.ingest(1L, org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
        .thenReturn(true);

    var result =
        new KsefConnectionController(
                client,
                KsefEnvironment.TEST,
                "1234567890",
                "secret-token",
                parser,
                ingestion,
                new ObjectMapper(),
                sources,
                canonical)
            .sync(1L, java.time.YearMonth.of(2026, 5));

    assertThat(result.received()).isEqualTo(3);
    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.duplicates()).isEqualTo(1);
    verify(client, org.mockito.Mockito.times(3))
        .queryIncomingInvoices(
            eq(KsefEnvironment.TEST),
            eq("access-token"),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class),
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class),
            eq(0),
            eq(250));
    verify(client, org.mockito.Mockito.never())
        .downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-3");
  }

  private static OffsetDateTime anyOffset(int year, int month, int day) {
    return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
  }
}
