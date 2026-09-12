package com.smartbox.investory.accounting;

import java.util.List;
import org.springframework.stereotype.Component;

/** Bridge adapter while the upload facade is migrated to the neutral extraction port. */
@Component
class LegacyInvoiceExtractorAdapter implements AccountingDocumentExtractor {
  private final PdfDocumentScanner pdf;

  LegacyInvoiceExtractorAdapter(PdfDocumentScanner pdf) {
    this.pdf = pdf;
  }

  @Override
  public boolean supports(AccountingSourceDocument source) {
    return pdf.supports(new DocumentInput(source.name(), source.contentType(), source.content()));
  }

  @Override
  public ExtractionResult extract(AccountingSourceDocument source) {
    DocumentScanResult result =
        pdf.scan(new DocumentInput(source.name(), source.contentType(), source.content()));
    if (result.invoice() == null) {
      return new ExtractionResult(
          null, ExtractorType.PDF_LAYOUT, ExtractionOutcome.REVIEW_REQUIRED, result.warnings());
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
            List.of(),
            "pdf-layout-v1");
    return new ExtractionResult(
        candidate,
        ExtractorType.PDF_LAYOUT,
        result.status() == ScanStatus.COMPLETE
            ? ExtractionOutcome.ACCEPTED
            : ExtractionOutcome.REVIEW_REQUIRED,
        result.warnings());
  }
}
