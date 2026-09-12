package com.smartbox.investory.poc.accounting.ksef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
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

    String redirect = controller.readInvoices("2026-07", attributes);

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
    when(ingestion.ingest(org.mockito.ArgumentMatchers.any(ReviewedInvoice.class)))
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

    controller.readInvoices("2026-07", attributes);

    ArgumentCaptor<ReviewedInvoice> captor = ArgumentCaptor.forClass(ReviewedInvoice.class);
    verify(ingestion).ingest(captor.capture());
    assertThat(captor.getValue().reference()).isEqualTo("FV-1");
    assertThat(captor.getValue().sourceQuality()).isEqualTo("KSEF_SOURCE_DOCUMENT");
    assertThat(captor.getValue().note()).contains("KSeF KSEF-1");
    assertThat(attributes.getFlashAttributes().get("ksefConnectionMessage"))
        .isEqualTo("KSeF metadata read; imported 1 new invoice(s); skipped 0.");
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
            eq("KSEF-UNKNOWN"),
            org.mockito.ArgumentMatchers.isNull(),
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

    controller.readInvoices("2026-07", new RedirectAttributesModelMap());

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
            eq(KsefEnvironment.TEST), eq("access-token"), eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)), eq(0), eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-BAD\"}]}");
    when(client.downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-BAD"))
        .thenReturn("<broken/>");
    when(sources.receiveKsef(eq("KSEF-BAD"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(11L);
    when(sources.status(11L)).thenReturn(AccountingSourceStatus.RECEIVED);
    when(parser.parse(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalArgumentException("invalid KSeF XML"));

    new KsefConnectionController(
            client, KsefEnvironment.TEST, "1234567890", "secret-token", parser, ingestion,
            new ObjectMapper(), sources)
        .readInvoices("2026-07", new RedirectAttributesModelMap());

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
    when(client.authenticateWithToken(KsefEnvironment.TEST, "1234567890", "secret-token"))
        .thenReturn(new KsefAccess("access-token", null, null, null));
    when(client.queryIncomingInvoices(
            eq(KsefEnvironment.TEST), eq("access-token"), eq(anyOffset(2026, 7, 1)),
            eq(anyOffset(2026, 8, 1)), eq(0), eq(250)))
        .thenReturn("{\"invoices\":[{\"ksefNumber\":\"KSEF-IMPORTED\"}]}");
    when(sources.findId(AccountingSourceType.KSEF, "KSEF-IMPORTED"))
        .thenReturn(Optional.of(12L));
    when(sources.status(12L)).thenReturn(AccountingSourceStatus.IMPORTED);

    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
    new KsefConnectionController(
            client, KsefEnvironment.TEST, "1234567890", "secret-token", parser, ingestion,
            new ObjectMapper(), sources)
        .readInvoices("2026-07", attributes);

    org.mockito.Mockito.verifyNoInteractions(parser, ingestion);
    org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
        .downloadInvoice(KsefEnvironment.TEST, "access-token", "KSEF-IMPORTED");
    assertThat(attributes.getFlashAttributes().get("ksefConnectionMessage"))
        .isEqualTo("KSeF metadata read; imported 0 new invoice(s); skipped 1.");
  }

  private static OffsetDateTime anyOffset(int year, int month, int day) {
    return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
  }
}
