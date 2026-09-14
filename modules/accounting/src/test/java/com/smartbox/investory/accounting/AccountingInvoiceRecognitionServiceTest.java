package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService;
import org.junit.jupiter.api.Test;

class AccountingInvoiceRecognitionServiceTest {
  @Test
  void returnsAReviewableCandidateWhenDirectionIsUnknown() {
    LayeredDocumentScanner scanner = mock(LayeredDocumentScanner.class);
    var candidate =
        new AccountingInvoiceRecognitionService.RecognizedInvoice(
            "UNKNOWN",
            null,
            null,
            null,
            "FV/2026/08/17",
            "ACME Sp. z o.o.",
            null,
            "OTHER",
            "PLN",
            null,
            null,
            null,
            "Direction needs review");
    when(scanner.scan(any(DocumentInput.class)))
        .thenReturn(
            new DocumentScanResult(
                candidate,
                ScannerType.AI_FALLBACK,
                ScanStatus.PARTIAL,
                0.0,
                java.util.List.of("invoice direction requires review")));

    assertThat(
            new AccountingInvoiceRecognitionService(scanner)
                .recognize("invoice.pdf", "application/pdf", new byte[] {1}))
        .isSameAs(candidate);
  }

  @Test
  void reportsParserFailureInsteadOfDereferencingMissingInvoice() {
    LayeredDocumentScanner scanner = mock(LayeredDocumentScanner.class);
    when(scanner.scan(any(DocumentInput.class)))
        .thenReturn(
            DocumentScanResult.incomplete(
                ScannerType.PDF_DETERMINISTIC, ScanStatus.PARTIAL, "missing invoice fields"));

    assertThatThrownBy(
            () ->
                new AccountingInvoiceRecognitionService(scanner)
                    .recognize("invoice.pdf", "application/pdf", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Document could not be parsed (PARTIAL): missing invoice fields");
  }
}
