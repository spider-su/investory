package com.smartbox.investory.accounting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LayeredDocumentScanner {
  private final PdfDocumentScanner pdfScanner;
  private final ImageDocumentScanner imageScanner;
  private final AiDocumentScanner aiScanner;
  private final DocumentScannerProperties properties;

  LayeredDocumentScanner(
      PdfDocumentScanner pdfScanner,
      ImageDocumentScanner imageScanner,
      AiDocumentScanner aiScanner,
      DocumentScannerProperties properties) {
    this.pdfScanner = pdfScanner;
    this.imageScanner = imageScanner;
    this.aiScanner = aiScanner;
    this.properties = properties;
  }

  public DocumentScanResult scan(long profileId, DocumentInput input) {
    DocumentScanner deterministic = select(input);
    if (deterministic != null) {
      log.info("document scanner selected: {}", scannerName(deterministic));
      DocumentScanResult result = deterministic.scan(profileId, input);
      if (result.isUsable()) return result;
      log.info("falling back to AI after {} result: {}", result.status(), result.warnings());
      if (!properties.isAiFallbackEnabled()) return result;
    } else {
      log.info("no deterministic document scanner available; falling back to AI");
    }
    if (properties.isAiFallbackEnabled()) return aiScanner.scan(profileId, input);
    return DocumentScanResult.incomplete(
        ScannerType.NONE, ScanStatus.UNSUPPORTED, "AI fallback is disabled");
  }

  private DocumentScanner select(DocumentInput input) {
    if (properties.isPdfEnabled() && pdfScanner.supports(input)) return pdfScanner;
    if (properties.isImageEnabled() && imageScanner.supports(input)) return imageScanner;
    return null;
  }

  private String scannerName(DocumentScanner scanner) {
    return scanner == pdfScanner ? "PDF" : "IMAGE";
  }
}
