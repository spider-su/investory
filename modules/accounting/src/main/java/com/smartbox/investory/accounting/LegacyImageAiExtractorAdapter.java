package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Neutral-port bridge for the existing image/AI path until OCR is installed as an adapter. */
@Component
class LegacyImageAiExtractorAdapter implements AccountingDocumentExtractor {
  private final ImageDocumentScanner image;
  private final AiDocumentScanner ai;

  LegacyImageAiExtractorAdapter(ImageDocumentScanner image, AiDocumentScanner ai) {
    this.image = image;
    this.ai = ai;
  }

  @Override
  public boolean supports(AccountingSourceDocument source) {
    return image.supports(new DocumentInput(source.name(), source.contentType(), source.content()));
  }

  @Override
  public ExtractionResult extract(AccountingSourceDocument source) {
    DocumentInput input = new DocumentInput(source.name(), source.contentType(), source.content());
    DocumentScanResult result = image.scan(input);
    if (!result.isUsable()) result = ai.scan(input);
    if (result.invoice() == null) {
      return new ExtractionResult(
          null, ExtractorType.OCR, ExtractionOutcome.REVIEW_REQUIRED, result.warnings());
    }
    var invoice = result.invoice();
    var candidate =
        new AccountingDocumentCandidate(
            invoice.documentType(),
            invoice.reference(),
            invoice.issueDate(),
            invoice.saleDate(),
            invoice.dueDate(),
            invoice.seller(),
            invoice.sellerNip(),
            invoice.buyer(),
            invoice.buyerNip(),
            invoice.currency(),
            invoice.netAmount(),
            invoice.vatAmount(),
            invoice.grossAmount(),
            List.of(),
            invoice.category(),
            evidence(
                invoice.evidence(),
                result.scannerType() == ScannerType.AI_FALLBACK
                    ? ExtractorType.AI
                    : ExtractorType.OCR),
            result.scannerType() == ScannerType.AI_FALLBACK ? "ai-v1" : "ocr-v1");
    ExtractorType type =
        result.scannerType() == ScannerType.AI_FALLBACK ? ExtractorType.AI : ExtractorType.OCR;
    return new ExtractionResult(
        candidate,
        type,
        result.status() == ScanStatus.COMPLETE
            ? ExtractionOutcome.ACCEPTED
            : ExtractionOutcome.REVIEW_REQUIRED,
        result.warnings());
  }

  private List<FieldCandidate<?>> evidence(
      List<AccountingInvoiceRecognitionService.FieldCandidate<?>> source, ExtractorType extractor) {
    if (source == null) return List.of();
    List<FieldCandidate<?>> result = new ArrayList<>();
    for (var field : source) {
      result.add(
          new FieldCandidate<Object>(
              field.value(),
              new ExtractionEvidence(
                  extractor, EvidenceType.valueOf(field.source().name()), field.evidence())));
    }
    return result;
  }
}
