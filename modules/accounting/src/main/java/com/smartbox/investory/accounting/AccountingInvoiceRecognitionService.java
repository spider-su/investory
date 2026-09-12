package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Public document-recognition seam used by the Accounting POC upload flow. */
@Service
@RequiredArgsConstructor
public class AccountingInvoiceRecognitionService {
  private final LayeredDocumentScanner scanner;

  public RecognizedInvoice recognize(String filename, String contentType, byte[] bytes) {
    return scanner.scan(new DocumentInput(filename, contentType, bytes)).invoice();
  }

  public record RecognizedInvoice(
      String documentType,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String seller,
      String buyer,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String note) {}
}
