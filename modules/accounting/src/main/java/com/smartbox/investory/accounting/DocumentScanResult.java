package com.smartbox.investory.accounting;

import java.util.List;

public record DocumentScanResult(
    AccountingInvoiceRecognitionService.RecognizedInvoice invoice,
    ScannerType scannerType,
    ScanStatus status,
    double confidence,
    List<String> warnings) {

  public boolean isUsable() {
    return status == ScanStatus.COMPLETE && invoice != null;
  }

  public static DocumentScanResult complete(
      AccountingInvoiceRecognitionService.RecognizedInvoice invoice,
      ScannerType scannerType,
      double confidence) {
    return new DocumentScanResult(invoice, scannerType, ScanStatus.COMPLETE, confidence, List.of());
  }

  public static DocumentScanResult incomplete(
      ScannerType scannerType, ScanStatus status, String warning) {
    return new DocumentScanResult(null, scannerType, status, 0.0, List.of(warning));
  }
}
