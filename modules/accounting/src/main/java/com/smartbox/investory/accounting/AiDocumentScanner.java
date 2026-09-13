package com.smartbox.investory.accounting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AiDocumentScanner implements DocumentScanner {
  private final AiInvoiceRecognitionClient client;
  private final InvoiceValidator validator;
  private final AccountingFactService factService;

  @Override
  public boolean supports(DocumentInput input) {
    return true;
  }

  @Override
  public DocumentScanResult scan(DocumentInput input) {
    AccountingInvoiceRecognitionService.RecognizedInvoice invoice =
        client.recognize(input.fileName(), input.contentType(), input.bytes());
    if (invoice == null) {
      throw new IllegalStateException("AI invoice recognition returned no result");
    }
    invoice = validator.resolveDirection(invoice, factService.accountingProfile().nip());
    var issues = validator.validate(invoice);
    if (!issues.isEmpty()) {
      log.info("AI invoice result requires review: {}", issues);
      return new DocumentScanResult(
          invoice, ScannerType.AI_FALLBACK, ScanStatus.PARTIAL, 0.0, issues);
    }
    log.info("AI fallback successful");
    return DocumentScanResult.complete(invoice, ScannerType.AI_FALLBACK, 0.8);
  }
}
