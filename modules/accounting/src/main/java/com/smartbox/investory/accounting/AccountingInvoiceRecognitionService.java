package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
      String note,
      String sellerNip,
      String buyerNip,
      List<FieldCandidate<?>> evidence) {
    public RecognizedInvoice(
        String documentType, LocalDate issueDate, LocalDate saleDate, LocalDate dueDate,
        String reference, String seller, String buyer, String category, String currency,
        BigDecimal netAmount, BigDecimal vatAmount, BigDecimal grossAmount, String note) {
      this(documentType, issueDate, saleDate, dueDate, reference, seller, buyer, category, currency,
          netAmount, vatAmount, grossAmount, note, null, null, List.of());
    }
  }

  public enum ExtractionSource { EXPLICIT_LABEL, TABLE_VALUE, ARITHMETIC_DERIVED, HEURISTIC, AI }

  public record FieldCandidate<T>(T value, ExtractionSource source, String evidence) {}
}
