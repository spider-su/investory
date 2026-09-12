package com.smartbox.investory.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PdfDocumentScanner implements DocumentScanner {
  private final PdfTextExtractor textExtractor;
  private final InvoiceTextParser textParser;

  @Override
  public boolean supports(DocumentInput input) {
    return DocumentTypeDetector.detect(input) == DocumentTypeDetector.Type.PDF;
  }

  @Override
  public DocumentScanResult scan(DocumentInput input) {
    try {
      String text = textExtractor.extract(input.bytes());
      log.info("PDF text extracted: {} chars", text.length());
      InvoiceTextParser.ParseResult parsed = textParser.parse(text);
      if (parsed.status() != ScanStatus.COMPLETE) {
        log.info("deterministic PDF parse incomplete: {}", String.join(", ", parsed.warnings()));
        return new DocumentScanResult(
            parsed.invoice(),
            ScannerType.PDF_DETERMINISTIC,
            parsed.status(),
            0.0,
            parsed.warnings());
      }
      return DocumentScanResult.complete(parsed.invoice(), ScannerType.PDF_DETERMINISTIC, 1.0);
    } catch (RuntimeException exception) {
      log.info("deterministic PDF scan failed; AI fallback required");
      return DocumentScanResult.incomplete(
          ScannerType.PDF_DETERMINISTIC, ScanStatus.FAILED, "PDF deterministic scan failed");
    }
  }
}
