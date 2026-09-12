package com.smartbox.investory.poc.accounting.ksef;

import com.smartbox.investory.accounting.AccountingDocumentCandidate;
import com.smartbox.investory.accounting.AccountingDocumentExtractor;
import com.smartbox.investory.accounting.AccountingSourceDocument;
import com.smartbox.investory.accounting.EvidenceType;
import com.smartbox.investory.accounting.ExtractionEvidence;
import com.smartbox.investory.accounting.ExtractionOutcome;
import com.smartbox.investory.accounting.ExtractionResult;
import com.smartbox.investory.accounting.ExtractorType;
import com.smartbox.investory.accounting.FieldCandidate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class KsefDocumentExtractorAdapter implements AccountingDocumentExtractor {
  private final KsefInvoiceXmlParser parser;

  KsefDocumentExtractorAdapter(KsefInvoiceXmlParser parser) {
    this.parser = parser;
  }

  @Override
  public boolean supports(AccountingSourceDocument source) {
    String type = source.contentType() == null ? "" : source.contentType().toLowerCase(Locale.ROOT);
    return type.contains("xml")
        || (source.name() != null && source.name().toLowerCase(Locale.ROOT).endsWith(".xml"));
  }

  @Override
  public ExtractionResult extract(AccountingSourceDocument source) {
    KsefInvoiceXmlParser.ParsedKsefInvoice value = parser.parse(source.content());
    var candidate =
        new AccountingDocumentCandidate(
            "UNKNOWN",
            value.reference(),
            value.issueDate(),
            value.saleDate(),
            null,
            value.sellerName(),
            value.sellerNip(),
            value.buyerName(),
            value.buyerNip(),
            value.currency(),
            value.netAmount(),
            value.vatAmount(),
            value.grossAmount(),
            List.of(),
            value.category(),
            List.of(
                new FieldCandidate<>(
                    value.reference(),
                    new ExtractionEvidence(
                        ExtractorType.KSEF, EvidenceType.STRUCTURED_SOURCE, "KSeF XML field P_2"))),
            "ksef-fa3-v1");
    return new ExtractionResult(
        candidate, ExtractorType.KSEF, ExtractionOutcome.REVIEW_REQUIRED, List.of());
  }
}
