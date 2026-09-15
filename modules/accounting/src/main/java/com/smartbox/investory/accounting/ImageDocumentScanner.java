package com.smartbox.investory.accounting;

import org.springframework.stereotype.Component;

/** Image scanner seam. OCR is intentionally deferred; images use the AI fallback for now. */
@Component
class ImageDocumentScanner implements DocumentScanner {
  @Override
  public boolean supports(DocumentInput input) {
    return DocumentTypeDetector.detect(input) == DocumentTypeDetector.Type.IMAGE;
  }

  @Override
  public DocumentScanResult scan(DocumentInput input) {
    return DocumentScanResult.incomplete(
        ScannerType.IMAGE, ScanStatus.UNSUPPORTED, "Image OCR is not implemented");
  }
}
