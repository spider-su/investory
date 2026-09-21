package com.smartbox.investory.ryczalt.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Native Ryczalt boundary for document recognition. It returns source facts only. */
public interface InvoiceRecognitionPort {
  RecognizedInvoice recognize(String filename, String contentType, byte[] content);

  record RecognizedInvoice(
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      Party seller,
      Party buyer,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String descriptionHint,
      String sourceMetadata,
      BigDecimal confidence) {}

  record Party(String legalName, String taxIdentifier, String country) {}
}
