package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AiDocumentScannerTest {
  @Test
  void keepsAnAmbiguousInvoiceForTheManualReviewFlow() {
    AiInvoiceRecognitionClient client = mock(AiInvoiceRecognitionClient.class);
    AccountingFactService facts = mock(AccountingFactService.class);
    when(facts.accountingProfile())
        .thenReturn(new AccountingProfile(true, "1111111111", null, null, null, null, null, null));
    when(client.recognize("invoice.pdf", "application/pdf", new byte[] {1}))
        .thenReturn(
            new AccountingInvoiceRecognitionService.RecognizedInvoice(
                "UNKNOWN",
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 17),
                null,
                "FV/2026/08/17",
                "ACME Sp. z o.o.",
                null,
                "OTHER",
                "PLN",
                new BigDecimal("1000.00"),
                new BigDecimal("230.00"),
                new BigDecimal("1230.00"),
                "Direction needs review",
                "1234567890",
                null,
                java.util.List.of()));

    DocumentScanResult result =
        new AiDocumentScanner(client, new InvoiceValidator(), facts)
            .scan(new DocumentInput("invoice.pdf", "application/pdf", new byte[] {1}));

    assertThat(result.status()).isEqualTo(ScanStatus.PARTIAL);
    assertThat(result.invoice()).isNotNull();
    assertThat(result.invoice().documentType()).isEqualTo("UNKNOWN");
    assertThat(result.warnings()).contains("invoice direction requires review");
  }
}
