package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class AccountingInvoiceRecognitionServiceTest {
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
