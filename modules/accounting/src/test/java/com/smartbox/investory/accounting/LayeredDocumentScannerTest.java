package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LayeredDocumentScannerTest {
  private static final DocumentInput PDF =
      new DocumentInput("invoice.pdf", "application/pdf", "%PDF-1.7".getBytes());
  private static final DocumentInput IMAGE =
      new DocumentInput("invoice.jpg", "image/jpeg", new byte[] {1, 2, 3});
  private static final DocumentInput UNKNOWN =
      new DocumentInput("invoice.bin", "application/octet-stream", new byte[] {1, 2, 3});

  @Test
  void doesNotCallAiForCompleteTextPdf() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    when(pdf.supports(PDF)).thenReturn(true);
    when(pdf.scan(1L, PDF))
        .thenReturn(DocumentScanResult.complete(invoice(), ScannerType.PDF_DETERMINISTIC, 1.0));

    DocumentScanResult result = scanner(pdf, image, ai).scan(1L, PDF);

    assertThat(result.isUsable()).isTrue();
    assertThat(result.scannerType()).isEqualTo(ScannerType.PDF_DETERMINISTIC);
    verify(ai, never()).scan(1L, PDF);
  }

  @Test
  void fallsBackWhenPdfIsPartial() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    when(pdf.supports(PDF)).thenReturn(true);
    when(pdf.scan(1L, PDF))
        .thenReturn(
            DocumentScanResult.incomplete(
                ScannerType.PDF_DETERMINISTIC, ScanStatus.PARTIAL, "missing VAT amount"));
    when(ai.scan(1L, PDF))
        .thenReturn(DocumentScanResult.complete(invoice(), ScannerType.AI_FALLBACK, .8));

    DocumentScanResult result = scanner(pdf, image, ai).scan(1L, PDF);

    assertThat(result.scannerType()).isEqualTo(ScannerType.AI_FALLBACK);
    verify(ai).scan(1L, PDF);
  }

  @Test
  void routesImagesThroughImageStubThenAi() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    when(image.supports(IMAGE)).thenReturn(true);
    when(image.scan(1L, IMAGE))
        .thenReturn(
            DocumentScanResult.incomplete(
                ScannerType.IMAGE, ScanStatus.UNSUPPORTED, "OCR not implemented"));
    when(ai.scan(1L, IMAGE))
        .thenReturn(DocumentScanResult.complete(invoice(), ScannerType.AI_FALLBACK, .8));

    DocumentScanResult result = scanner(pdf, image, ai).scan(1L, IMAGE);

    assertThat(result.scannerType()).isEqualTo(ScannerType.AI_FALLBACK);
    verify(image).scan(1L, IMAGE);
    verify(ai).scan(1L, IMAGE);
  }

  @Test
  void routesUnsupportedDocumentsToAi() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    when(ai.scan(1L, UNKNOWN))
        .thenReturn(DocumentScanResult.complete(invoice(), ScannerType.AI_FALLBACK, .8));

    DocumentScanResult result = scanner(pdf, image, ai).scan(1L, UNKNOWN);

    assertThat(result.scannerType()).isEqualTo(ScannerType.AI_FALLBACK);
    verify(ai).scan(1L, UNKNOWN);
  }

  @Test
  void preservesAiFailure() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    when(ai.scan(1L, UNKNOWN)).thenThrow(new IllegalStateException("AI unavailable"));

    assertThatThrownBy(() -> scanner(pdf, image, ai).scan(1L, UNKNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AI unavailable");
  }

  @Test
  void doesNotCallAiWhenFallbackIsDisabled() {
    PdfDocumentScanner pdf = mock(PdfDocumentScanner.class);
    ImageDocumentScanner image = mock(ImageDocumentScanner.class);
    AiDocumentScanner ai = mock(AiDocumentScanner.class);
    DocumentScannerProperties properties = new DocumentScannerProperties();
    properties.setAiFallbackEnabled(false);

    DocumentScanResult result =
        new LayeredDocumentScanner(pdf, image, ai, properties).scan(1L, UNKNOWN);

    assertThat(result.status()).isEqualTo(ScanStatus.UNSUPPORTED);
    assertThat(result.warnings()).containsExactly("AI fallback is disabled");
    verify(ai, never()).scan(1L, UNKNOWN);
  }

  private LayeredDocumentScanner scanner(
      PdfDocumentScanner pdf, ImageDocumentScanner image, AiDocumentScanner ai) {
    return new LayeredDocumentScanner(pdf, image, ai, new DocumentScannerProperties());
  }

  private AccountingInvoiceRecognitionService.RecognizedInvoice invoice() {
    return new AccountingInvoiceRecognitionService.RecognizedInvoice(
        "PURCHASE_INVOICE",
        LocalDate.of(2026, 3, 12),
        null,
        null,
        "FV 123/2026",
        "Demo Seller",
        "Demo Buyer",
        "OTHER",
        "PLN",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        new BigDecimal("123.00"),
        "test");
  }
}
