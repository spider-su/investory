package com.smartbox.investory.accounting;

public interface AccountingDocumentExtractor {
  boolean supports(AccountingSourceDocument source);

  ExtractionResult extract(AccountingSourceDocument source);
}
