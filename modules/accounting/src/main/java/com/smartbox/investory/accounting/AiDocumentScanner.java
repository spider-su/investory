package com.smartbox.investory.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AiDocumentScanner implements DocumentScanner {
  private final AiInvoiceRecognitionClient client;

  @Override
  public boolean supports(DocumentInput input) {
    return true;
  }

  @Override
  public DocumentScanResult scan(DocumentInput input) {
    AccountingInvoiceRecognitionService.RecognizedInvoice invoice =
        client.recognize(input.fileName(), input.contentType(), input.bytes());
    log.info("AI fallback successful");
    return DocumentScanResult.complete(invoice, ScannerType.AI_FALLBACK, 0.8);
  }
}
