package com.smartbox.investory.accounting;

public interface DocumentScanner {
  boolean supports(DocumentInput input);

  DocumentScanResult scan(DocumentInput input);
}
